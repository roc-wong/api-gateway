package com.xuxl.common.annotation.http.api;

/**
 * Created by roc on 2017/8/20.
 */
public @interface ApiParameter {
    String desc();

    String name();

    boolean required();
}
