package com.xuxl.common.annotation.dubbo.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DubboService {
	
	String version();
	
	int retries() default 0;
	
	int timeout() default 6000;

}
