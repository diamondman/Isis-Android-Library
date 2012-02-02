package com.diamondman.android.isis;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

/**
 * Base service provides the facilities common to all Services used in this system. It creates a 
 * messenger for the Service, and a Handler on a new Thread for the messenger to use. It also 
 * provides synchronization for the new thread so binding the service will not fail due to the
 * messenger not being created yet. <br/><br/>
 * 
 * It is suggested that all Services subclassing from this class create a static reference to 
 * themselves and a public static getter method for clients to get ahold of the running service. 
 * If you do this, be sure to set proper protection on your methods to prevent bad things from 
 * happening. (This class tried to provide this functionality, but the static keyword does not 
 * allow a super class to give subclass their own static objects.
 * 
 * @author Jessy Diamond Exum
 *
 */
public abstract class IsisService extends Service {
	/**
	 * The Service’s default internal message queue. No one outside of this class should need 
	 * this object since send(), it’s overrides, and handleMessage() should be all anything 
	 * needs. If you really need to directly touch the Messenger, use getMessenger();<br><br>
	 * 
	 * DO NOT MAKE THIS PUBLIC OR PROTECTED OR YOU ARE GOING TO END UP USING A NULL OBJECT IF 
	 * YOU REQUEST IT TOO EARLY! ALL PROVIDED METHODS SHOULD BE ENOUGH TO DO EVERYTHING ONE 
	 * COULD WANT TO DO!
	 */
	private volatile Messenger mMessenger;
	/**
	 * The services internal message looper thread.
	 */
	protected ServiceThread serviceThread;
	/**
	 * An object to synchronize around until mMessenger is created.
	 */
	protected final AtomicBoolean messengerCreated = new AtomicBoolean(false);
	
	private static ConcurrentHashMap<Class<? extends IsisService>,IsisService> services = 
				new ConcurrentHashMap<Class<? extends IsisService>,IsisService>();
	private static <T extends IsisService>void registerServiceType(Class<T> c, IsisService s)
	{
		services.put(c, s);
	}
	@SuppressWarnings("unchecked")
	public static <T extends IsisService>T getServiceInstance(Class<T> c){
		return (T) services.get(c);
	}
	
	@Override
	public final IBinder onBind(Intent arg0) {
		synchronized(messengerCreated){
			if(messengerCreated.get()==false){
				try {
					messengerCreated.wait();
				} catch (InterruptedException e) {}
			}
		}
		return mMessenger.getBinder();
	}
	/**
	 * Sends the Service a message.
	 * @param msg
	 */
	public void send(Message msg){
		try {
			mMessenger.send(msg);
		} catch (RemoteException e) {}
	}
	public void send(Integer what, Integer arg1, Integer arg2, Object obj){
		send(Message.obtain(null, what, arg1,arg2,obj));
	}
	public void send(Integer what, Integer arg1, Integer arg2){
		send(Message.obtain(null, what, arg1,arg2));
	}
	public void send(Integer what){send(Message.obtain(null, what));}
	
	/**
	 * DON’T CALL THIS DIRECTLY! THIS IS FOR ANDROID TO USE!<br><br>
	 * 
	 * Creates the serviceThread and starts it.
	 */
	@Override
	public void onCreate(){
		super.onCreate();
		registerServiceType(this.getClass(), this);
		serviceThread = new ServiceThread();
		serviceThread.start();
	}
	
	/**
	 * DON’T CALL THIS DIRECTLY! THIS IS FOR ANDROID TO USE!<br><br>
	 */
	public void onDestroy(){
		super.onDestroy();
		serviceThread.handler.getLooper().quit();
	}
	
	/**
	 * This class is the local thread used by the wrapper for the message queue and handler. 
	 * It also synchronizes the Messenger object for use with onBind.
	 *
	 * @author Jessy Diamond Exum
	 *
	 */
	private class ServiceThread extends Thread{
		Handler handler;
		@Override
		public void run(){
			Thread.currentThread().setName(this.getClass().getSimpleName());
			Looper.prepare();
			handler = new Handler(){
				@Override
				public void handleMessage(Message msg) {
					IsisService.this.handleMessage(msg);
				}
			};
			synchronized(messengerCreated){
				mMessenger = new Messenger(handler);
				messengerCreated.notifyAll();
				messengerCreated.set(true);
			}
			Looper.loop();
		}
	}
	
	/**
	 * Override this method to handle the Service’s messages. This is where most event 
	 * dispatching logic will be, and potentially control logic depending on the Service 
	 * design design.
	 * @param msg Message sent to the Service.
	 */
	protected abstract void handleMessage(Message msg);
}