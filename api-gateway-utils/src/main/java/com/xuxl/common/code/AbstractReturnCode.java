package com.xuxl.common.code;

import java.io.Serializable;

public abstract class AbstractReturnCode implements Serializable {

	private static final long serialVersionUID = 1L;
	
    private final String desc;
    
    private final int code;

    public AbstractReturnCode(String desc, int code) {
        this.desc = desc;
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public int getCode() {
        return code;
    }
}
