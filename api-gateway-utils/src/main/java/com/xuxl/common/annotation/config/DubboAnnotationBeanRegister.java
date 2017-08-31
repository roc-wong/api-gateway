package com.xuxl.common.annotation.config;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;


public class DubboAnnotationBeanRegister implements ImportBeanDefinitionRegistrar {

	public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry registry) {
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(annotationMetadata.getAnnotationAttributes(EnableDubboAnnotationBeanRegister.class.getName()));
        String[] packages = attributes.getStringArray("basePackages");
        BeanDefinitionBuilder dubboProviderBean = BeanDefinitionBuilder.genericBeanDefinition(DubboAnnotationBean.class);
        dubboProviderBean.addConstructorArgValue(packages);
        registry.registerBeanDefinition("dubboProviderBean", dubboProviderBean.getRawBeanDefinition());
	}

}
