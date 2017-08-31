package com.xuxl.common.exception;

import java.io.Serializable;

import com.xuxl.common.code.AbstractReturnCode;

public class ServiceException extends RuntimeException implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private int code;
	
	private String msg;

	public ServiceException(AbstractReturnCode code) {
		this(code, code.getDesc());
	}

	public ServiceException(AbstractReturnCode code, String msg) {
		super("code:" + code.getCode() + ",msg:" + msg);
		this.code = code.getCode();
		this.msg = msg;
	}

	public int getCode() {
		return code;
	}

	public String getMsg() {
		return msg;
	}

}
