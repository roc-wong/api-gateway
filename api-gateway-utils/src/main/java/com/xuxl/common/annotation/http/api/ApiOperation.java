package com.xuxl.common.annotation.http.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiOperation {
	
    /**
     * Http 接口名
     *
     * @return
     */
    String value();

    /**
     * Http 接口注释
     *
     * @return
     */
    String desc();


    /**
     * 接口负责人
     *
     * @return
     */
    String owner() default "";
    
    /**
     * 请求方式,GET,POST
     * @return
     */
    String method();
    
    

}
