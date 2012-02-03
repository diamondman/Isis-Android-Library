package com.diamondman.android.isis;

public class BindFailedException extends RuntimeException {
	private static final long serialVersionUID = 6452614162540268401L;
	public BindFailedException(String msg) {
		super(msg);
	}
	public BindFailedException(String msg, Throwable cause) {
		super(msg,cause);
	}
	public BindFailedException() {
		super();
	}
}
