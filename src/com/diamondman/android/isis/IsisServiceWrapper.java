package com.diamondman.android.isis;

import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

/**
 * The services all started out using message loops and the clients were
 * supposed to make requests for data before it was needed. This worked well,
 * but was hard for others to understand. Because of this, the Service wrapper
 * class and it’s subclasses were created to allow the Message loop to remain
 * without putting the client writers in a position of dealing with sending a
 * message requesting data and then having to wait for a response (and then save
 * a local copy of that data). As the code has further evolved, the Message
 * loops everywhere are starting to look unnecessary, but shall remain for now.
 * Even if the messengers are removed, indivual subclasses can still redefine it
 * for themselves, and all of the normal subclasses should work the same.<br/>
 * <br/>
 * 
 * ServiceWrapper creates a Message queue, a Handling Thread, and a handler, and
 * binds to a Service specified in the constructor. It also provides empty
 * methods that are naturally part of misc service connection related classes.
 * This allows easier overriding of the behavior as children don’t have to ever
 * see the ServiceConnection object or register any methods.<br/>
 * <br/>
 * 
 * While subclassing this class, defining the generic in the braces is a must,
 * as most of the automatic work this class does is based on that information
 * being correct. An example wrapper class for an imaginary service called
 * ‘testService’ would look like this:<br/>
 * public class testWrapper extends<testService>{...<br/>
 * <br/>
 * 
 * When a ServiceWrapper is no longer needed, the release method should be
 * called on it. This will destroy the messenger and handler and thread. All
 * subClasses should override this method and send any messages they need to
 * their respective service to make sure the Messanger object is not held onto
 * by anything and therefore allowed to be garbage collected.<br/>
 * <br/>
 * 
 * When a subclass inevitably needs a direct handle to it’s wrapped service so
 * it can directly call functions from it, simply call getService().
 * getService() handles casting to the appropriate type (thanks to generics) and
 * also handles synchronization to guarantee that the handle you get is valid.
 * 
 * @author Jessy Diamond Exum
 * 
 */
public abstract class IsisServiceWrapper<T extends IsisService> {
	private volatile AtomicBoolean	messengerCreated	= new AtomicBoolean(false);
	private Messenger				mMessenger;
	private Messenger				mService			= null;
	private Object					serviceHandleLock	= new Object();
	private Class<T>				t;
	/**
	 * The context object passed into the wrapper during creation. Use it to
	 * call android specific functions as the creating context.
	 */
	protected Context				context;
	
	/**
	 * A handle to the bound Service, if it has been assigned.
	 */
	private T						serviceHandle;

	/**
	 * Used to get ahold of the Wrapped BaseService object. While other classes
	 * can easily get a copy of the BaseService, it is suggested not to let this
	 * leak by say, providing a public getService method that returns this
	 * method. Try and make your Wrappers have functions to do what you want
	 * instead of having other classes muddle around in the interior of the
	 * Services and Wrappers.
	 * 
	 * @return Handle to the instance of the BaseService this wrapper is
	 *         wrapping.
	 */
	protected final T getService() {
		synchronized (serviceHandleLock) {
			while (serviceHandle == null)
				try {
					serviceHandleLock.wait();
				} catch (InterruptedException e) {}
			return serviceHandle;
		}
	}

	private WrapperThread	procThread;

	/**
	 * Get the ServiceWrapper’s internal Messenger object. Use this for sending
	 * the Messenger to Services for registration to events. For sending
	 * messages to the Service, use send();<br>
	 * <br>
	 * 
	 * This method waits until mMessenger is created to return it.
	 * 
	 * @return The Service’s internal Message queue.
	 */
	protected Messenger getMessenger() {
		synchronized (messengerCreated) {
			if (!messengerCreated.get()) try {
				messengerCreated.wait();
			} catch (InterruptedException e) {}
		}
		return mMessenger;
	}

	private ServiceConnection	mConnection	= new ServiceConnection() {
												public void onServiceConnected(ComponentName className, IBinder service) {
													synchronized (serviceHandleLock) {
														serviceHandle = IsisService.getServiceInstance(t);
														serviceHandleLock.notifyAll();
													}
													mService = new Messenger(service);
													// Check if running these
													// operations (following) on
													// the wrapper’s personal
													// thread would be better to
													// prevent deadlocks
													// procThread.post
													doRegistration();
													onInitialized();
												}

												public void onServiceDisconnected(ComponentName className) {
													IsisServiceWrapper.this.onServiceDisconnected(className);
													mService = null;
												}
											};

	private class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			IsisServiceWrapper.this.handleMessage(msg);
		}
	}

	/**
	 * Override this to be able to handle Messages from the wrapped Service.
	 * 
	 * @param msg
	 */
	protected abstract void handleMessage(Message msg);

	/**
	 * If you need a place to safely register the wrapper with the parent
	 * service, this is the method you should override.
	 */
	protected abstract void doRegistration();

	/**
	 * Think of this as the onCreate method of the Wrapper. From this method, it
	 * should be safe to do anything you need as everything is guaranteed to be
	 * ready.
	 */
	protected abstract void onInitialized();

	protected void onServiceDisconnected(ComponentName className) {}

	/**
	 * 
	 * @param context
	 *            The calling object’s Context
	 * @param intent
	 *            An Intend specifying the Service to Bind to. This is usually
	 *            new Intent(context, ServiceClass.class)
	 * @throws BindFailedException
	 */
	protected IsisServiceWrapper(Context context, Class<T> t) throws BindFailedException {
		this.t = t;
		this.context = context;
		procThread = new WrapperThread();
		procThread.start();
		boolean results = context.bindService(new Intent(context, t), mConnection, Service.BIND_AUTO_CREATE);
		if(!results) throw new BindFailedException();
		//Log.d("SERVICE WRAPPER", "Wrapper for " + t + " creating!");
	}

	/**
	 * This class is the local thread used by the wrapper for the message queue
	 * and handler. It also synchronizes the Messenger object so any sort of
	 * 
	 * @author Jessy Diamond Exum
	 * 
	 */
	private class WrapperThread extends Thread {
		IncomingHandler	handler;

		@Override
		public void run() {
			Thread.currentThread().setName(t.getSimpleName() + " Wrapper");
			Looper.prepare();
			handler = new IncomingHandler();
			synchronized (messengerCreated) {
				mMessenger = new Messenger(handler);
				messengerCreated.set(true);
				messengerCreated.notifyAll();
			}
			Looper.loop();
		}
	}

	/**
	 * <strike>This is the same as this.mService.send()</strike> DISREGARD THAT
	 * mService IS NOW PRIVATE!<BR>
	 * <BR>
	 * 
	 * Use this method to send messages directly to the Service. Be careful and
	 * make sure you know exactly what you are doing before you send messages
	 * directly to the Service wrapped by this object. The message object you
	 * provide will automatically have it’s replyTo set to the message loop of
	 * the wrapper, so if you are calling this from outside the ServiceWrapper,
	 * don’t expect it to reply to your external Message loop, you will have to
	 * bind to it the old fashion way or modify the specific wrapper class to do
	 * what you want.
	 * 
	 * @param message
	 *            message to send the Service.
	 */
	protected void send(Message message) {
		if (mService != null) {
			message.replyTo = this.mMessenger;
			try {
				mService.send(message);
			} catch (RemoteException e) {}
		}
	}

	protected void send(int what, int arg1, int arg2, Object obj) {
		send(Message.obtain(null, what, arg1, arg2, obj));
	}

	protected void send(int what) {
		send(Message.obtain(null, what, 0, 0, null));
	}

	/**
	 * ALWAYS CALL THIS METHOD WHEN THE OBJECT USING THIS OBJECT IS GOING AWAY
	 * OR YOU WILL HAVE EXTRA THREADS EVERYWHERE!!!!!!!!!!<br/>
	 * <br/>
	 * 
	 * NO SERIOUSLY, DON’T SCREW THIS UP!<br/>
	 * <br/>
	 * 
	 * Or you could just have the object using this object extend BaseActivity
	 * (assuming it is an activity) since BaseActivity automagically releases
	 * all ServiceWrappers.<br/>
	 * <br/>
	 * 
	 * Quits the looper and unbinds the service. If you have not registered the
	 * Wrapper with the Service in some way and have not created any other
	 * resources that should die when the wrapper dies, this may not need to be
	 * overridden. Buy in the highly likely event that either of the previous
	 * conditions are false, you should release and unregister everything before
	 * calling this method because the Service may terminate if the last client
	 * has disconnected, and then your messenger may be stuck in limbo and
	 * possibly never garbage collected.
	 */
	public void release() {
		procThread.handler.getLooper().quit();
		context.unbindService(mConnection);
	}
}