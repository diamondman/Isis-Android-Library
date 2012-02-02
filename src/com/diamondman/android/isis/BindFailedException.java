package com.diamondman.android.isis;

@SuppressWarnings("serial")
public class BindFailedException extends Exception {
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
