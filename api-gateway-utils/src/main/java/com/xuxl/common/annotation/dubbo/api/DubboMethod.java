package com.xuxl.common.annotation.dubbo.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DubboMethod {
	
	int retries() default 0;
	
	int timeOut() default 1000;

}
