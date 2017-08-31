package com.xuxl.apigateway.controller;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.xuxl.apigateway.code.SystemReturnCode;
import com.xuxl.apigateway.common.ApiInfo;
import com.xuxl.apigateway.common.ApiParameterInfo;
import com.xuxl.apigateway.common.BaseResponse;
import com.xuxl.apigateway.converter.StringToDateConverter;
import com.xuxl.apigateway.hystrix.DubboHyStrixCommand;
import com.xuxl.apigateway.listener.RestApiParseListener;
import com.xuxl.common.exception.ServiceException;
import com.xuxl.common.utils.JsonUtil;

@RestController
@RequestMapping(value = "/api", method = { RequestMethod.GET, RequestMethod.POST })
public class MultiController {

	private final static Logger logger = LoggerFactory.getLogger(MultiController.class);

	@RequestMapping("/{prefix}/{suffix}")
	public BaseResponse<Object> multi(HttpServletRequest request, @PathVariable String prefix,
			@PathVariable String suffix) throws Exception {
		logger.info(String.format("ClientIp: %s,Url:%s", getClientIp(request), getRequestInfo(request)));
		String requestMethod = request.getMethod();
		String mt = prefix + RestApiParseListener.SEPARATOR + suffix;
		ApiInfo apiInfo = RestApiParseListener.getRegisterMap().get(mt);
		if (apiInfo == null) {
			logger.error(String.format("%s is error", mt));
			throw new ServiceException(SystemReturnCode.UNKNOWN_METHOD_ERROR);
		}
		String type = apiInfo.getMethodInfo().getType();
		if (!requestMethod.equalsIgnoreCase(type)) {
			logger.error(String.format("%s is error,requestMethod must be %s", mt, type.toUpperCase()));
			throw new ServiceException(SystemReturnCode.REQUEST_METHOD_ERROR);
		}
		Object proxy = apiInfo.getProxy();
		if (proxy != null) {
			logger.error(String.format("%s参数没有对应的处理器", mt));
			throw new ServiceException(SystemReturnCode.DUBBO_SERVICE_NOTFOUND_ERROR);
		}
		Method method = apiInfo.getMethod();
		if (method != null) {
			logger.error(String.format("%s参数没有对应的处理方法", mt));
			throw new ServiceException(SystemReturnCode.UNKNOWN_METHOD_ERROR);
		}
		int timeout = apiInfo.getTimeout();
		ApiParameterInfo[] apiParameterInfos = apiInfo.getParameterInfos();
		Object[] args = parseParamater(apiParameterInfos, request);
		BaseResponse<Object> response = new DubboHyStrixCommand(proxy, method, args, timeout).execute();
		return response;
	}

	private String getRequestInfo(HttpServletRequest request) {
		StringBuilder builder = new StringBuilder(request.getRequestURI());
		StringJoiner jStringJoiner = new StringJoiner("&");
		Map<String, String[]> map = request.getParameterMap();
		map.forEach((key, value) -> {
			StringBuilder sb = new StringBuilder(key);
			sb.append("=");
			StringJoiner joiner = new StringJoiner(",");
			Arrays.stream(value).forEach(val -> joiner.add(val));
			sb.append(joiner.toString());
			jStringJoiner.add(sb);
		});
		if (jStringJoiner.toString().length() > 0) {
			builder.append("?");
			builder.append(jStringJoiner.toString());
		}
		return builder.toString();
	}

	private String getClientIp(HttpServletRequest request) {
		String ip = request.getHeader("X-Forwarded-For");
		if (StringUtils.hasText(ip) && !"unKnown".equalsIgnoreCase(ip)) {
			int index = ip.indexOf(",");
			if (index != -1) {
				return ip.substring(0, index);
			} else {
				return ip;
			}
		}
		ip = request.getHeader("X-Real-IP");
		if (StringUtils.hasText(ip) && !"unKnown".equalsIgnoreCase(ip)) {
			return ip;
		}
		return request.getRemoteAddr();
	}

	private Object[] parseParamater(ApiParameterInfo[] parameters, HttpServletRequest request) throws ServiceException {
		if (parameters == null) {
			return null;
		} else {
			Object[] objects = new Object[parameters.length];
			for (int i = 0, size = parameters.length; i < size; i++) {
				ApiParameterInfo apiParameterInfo = parameters[i];
				String name = apiParameterInfo.getName();
				String value = request.getParameter(name);
				Class<?> clazz = apiParameterInfo.getClazz();
				Class<?> genericClazz = apiParameterInfo.getGenericClazz();
				// 对基本数据类型,包装类，list,日期需要在request中直接取，没有就抛异常
				if (!isSimpleType(clazz)) {
					// 不支持map
					if (Map.class.isAssignableFrom(clazz)) {
						logger.error("unsupport map type");
						throw new ServiceException(SystemReturnCode.PARAMETER_ERROR);
					}
					// 不支持接口
					if (clazz.isInterface()) {
						logger.error(String.format("%s type is interface : %s", name, clazz.getTypeName()));
						throw new ServiceException(SystemReturnCode.PARAMETER_ERROR);
					}
					// 不支持抽象类
					if (Modifier.isAbstract(clazz.getModifiers())) {
						logger.error(String.format("%s type is abstract : %s", name, clazz.getTypeName()));
						throw new ServiceException(SystemReturnCode.PARAMETER_ERROR);
					}
					try {
						if (StringUtils.isEmpty(value)) {
							MutablePropertyValues mvps = new MutablePropertyValues(request.getParameterMap());
							Object object = BeanUtils.instantiateClass(clazz);
							BeanWrapper beanWrapper = (BeanWrapper) getTypeConverter(object);
							beanWrapper.setPropertyValues(mvps, true, true);
							objects[i] = beanWrapper.convertIfNecessary(object, clazz);
						} else {
							objects[i] = JsonUtil.convertObject(value, clazz);
						}
					} catch (Exception e) {
						logger.error(String.format("parse %s field has error, please check!", name));
						throw new ServiceException(SystemReturnCode.PARAMETER_ERROR);
					}
				} else {
					if (StringUtils.isEmpty(value)) {
						if (apiParameterInfo.isRequired()) {
							logger.error(String.format("%s field is required, so this can not be null", name));
							throw new ServiceException(SystemReturnCode.PARAMETER_ERROR);
						} else {
							String defaultValue = apiParameterInfo.getDefaultValue();
							objects[i] = fillValueWithDefaultValue(defaultValue, clazz);
						}
					} else {
						objects[i] = fillValue(value, clazz, genericClazz);
					}
				}
			}
			return objects;
		}
	}

	private boolean isSimpleType(Class<?> clazz) {
		return ClassUtils.isPrimitiveOrWrapper(clazz) || Date.class.isAssignableFrom(clazz)
				|| String.class.isAssignableFrom(clazz) || clazz.isEnum() || Collection.class.isAssignableFrom(clazz)
				|| clazz.isArray();
	}

	private Object fillValueWithDefaultValue(String defaultValue, Class<?> clazz) {
		if (clazz.isAssignableFrom(int.class) || clazz.isAssignableFrom(Integer.class)) {
			if (StringUtils.hasText(defaultValue)) {
				return Integer.parseInt(defaultValue);
			} else {
				return 0;
			}
		} else if (clazz.isAssignableFrom(String.class)) {
			if (StringUtils.hasText(defaultValue)) {
				return defaultValue;
			} else {
				return null;
			}
		} else if (clazz.isAssignableFrom(double.class) || clazz.isAssignableFrom(Double.class)) {
			if (StringUtils.hasText(defaultValue)) {
				return Double.parseDouble(defaultValue);
			} else {
				return 0.0d;
			}
		} else if (clazz.isAssignableFrom(short.class) || clazz.isAssignableFrom(Short.class)) {
			if (StringUtils.hasText(defaultValue)) {
				return Short.parseShort(defaultValue);
			} else {
				return 0;
			}
		} else if (clazz.isAssignableFrom(long.class) || clazz.isAssignableFrom(Long.class)) {
			if (StringUtils.hasText(defaultValue)) {
				return Long.parseLong(defaultValue);
			} else {
				return 0L;
			}
		} else if (clazz.isAssignableFrom(byte.class) || clazz.isAssignableFrom(Byte.class)) {
			if (StringUtils.hasText(defaultValue)) {
				return Byte.parseByte(defaultValue);
			} else {
				return 0;
			}
		} else if (clazz.isAssignableFrom(Date.class)) {
			if (StringUtils.hasText(defaultValue)) {
				Long content = Long.parseLong(defaultValue);
				return new Date(content);
			} else {
				return null;
			}
		} else if (clazz.isAssignableFrom(boolean.class) || clazz.isAssignableFrom(Boolean.class)) {
			if (StringUtils.hasText(defaultValue)) {
				return Boolean.parseBoolean(defaultValue);
			} else {
				return false;
			}
		} else if (clazz.isAssignableFrom(float.class) || clazz.isAssignableFrom(Float.class)) {
			if (StringUtils.hasText(defaultValue)) {
				return Float.parseFloat(defaultValue);
			} else {
				return 0.0f;
			}
		} else if (clazz.isAssignableFrom(char.class) || clazz.isAssignableFrom(Character.class)) {
			if (StringUtils.hasText(defaultValue)) {
				return defaultValue.charAt(0);
			} else {
				return '\u0000';
			}
		} else {
			return null;
		}
	}

	private Object fillValue(String value, Class<?> clazz, Class<?> genericParameterType) {
		if (clazz.isAssignableFrom(int.class) || clazz.isAssignableFrom(Integer.class)) {
			return Integer.parseInt(value);
		} else if (clazz.isAssignableFrom(String.class)) {
			return value;
		} else if (clazz.isAssignableFrom(double.class) || clazz.isAssignableFrom(Double.class)) {
			return Double.parseDouble(value);
		} else if (clazz.isAssignableFrom(short.class) || clazz.isAssignableFrom(Short.class)) {
			return Short.parseShort(value);
		} else if (clazz.isAssignableFrom(long.class) || clazz.isAssignableFrom(Long.class)) {
			return Long.parseLong(value);
		} else if (clazz.isAssignableFrom(byte.class) || clazz.isAssignableFrom(Byte.class)) {
			return Byte.parseByte(value);
		} else if (clazz.isAssignableFrom(Date.class)) {
			Long content = Long.parseLong(value);
			return new Date(content);
		} else if (clazz.isAssignableFrom(boolean.class) || clazz.isAssignableFrom(Boolean.class)) {
			return Boolean.parseBoolean(value);
		} else if (clazz.isAssignableFrom(char.class) || clazz.isAssignableFrom(Character.class)) {
			return value.charAt(0);
		} else if (clazz.isAssignableFrom(float.class) || clazz.isAssignableFrom(Float.class)) {
			return Float.parseFloat(value);
		} else if (clazz.isEnum()) {
			return getTypeConverter(null).convertIfNecessary(value, clazz);
		} else if (clazz.isArray()) {
			if (BeanUtils.isSimpleProperty(genericParameterType)) {
				return getTypeConverter(null).convertIfNecessary(value, clazz);
			} else {
				return JsonUtil.convertObject(value, clazz);
			}
		} else if (Collection.class.isAssignableFrom(clazz)) {
			if (BeanUtils.isSimpleProperty(genericParameterType)) {
				return getTypeConverter(null).convertIfNecessary(value, clazz);
			} else {
				return JsonUtil.convertCollection(value, clazz, genericParameterType);
			}
		}
		return null;

	}

	private TypeConverter getTypeConverter(Object object) {
		DefaultConversionService service = new DefaultConversionService();
		service.addConverter(new StringToDateConverter());
		if (Objects.nonNull(object)) {
			BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(object);
			wrapper.setConversionService(service);
			return wrapper;
		} else {
			SimpleTypeConverter converter = new SimpleTypeConverter();
			converter.setConversionService(service);
			return converter;
		}
	}

}
