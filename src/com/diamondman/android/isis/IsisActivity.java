package com.diamondman.android.isis;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.app.Activity;
import android.util.Log;
import android.view.Menu;

/**
 * This is one of the convenience classes for easing integration of Activities
 * with the service model. All Activities That use Services that have service
 * wrappers are urged to extend this class. All activities that extend this
 * class get the benefit that common pieces of code are wrapped into functions,
 * and on Destruction of the activity, this class looks for all ServiceWrapper
 * (and subclasses) objects owned by the Activity and release them, avoiding the
 * problem of ‘ooops, I forgot to release the 5 service wrappers I was using...
 * that is why 5 threads are being created everytime I open this Activity and
 * then never released.’
 * 
 * @author Jessy Diamond Exum
 * 
 */
public class IsisActivity extends Activity {

	private Integer	menuResource	= null;

	/**
	 * Sets the Menu Resource of the activity. If not set, or set to null, the
	 * menu will not appear when the user presses the menu key. If a menu is
	 * assigned, and the menu button is pressed, and then a null menu is
	 * assigned, the old menu will be shown because OnCreateOptionsMenu is only
	 * called once by Android.
	 * 
	 * @param menuResourceId
	 *            The Menu Resource Id (R.menu.whatever) of the desired menu for
	 *            the Activity.
	 */
	protected void setMenu(Integer menuResourceId) {
		menuResource = menuResourceId;
	}

	/**
	 * Reflect on subclass and call release on all sub classes of ServiceWrapper
	 * (to automatically clean up all servicewrapper resources on Activity
	 * destruction).
	 */
	private void releaseServices() {
			try {
				Class<? extends IsisActivity> c = this.getClass();
				Field[] f = c.getDeclaredFields();
				Method m = IsisServiceWrapper.class.getMethod("release", (Class[]) null);

				for (int i = 0; i < f.length; i++) {
					Class<?> c2 = f[i].getType();
					if(IsisServiceWrapper.class.isAssignableFrom(c2)) {
						Object o = null;
						try {
							o = f[i].get(this);
							m.invoke(o, (Object[]) null);
						} catch (Exception e) {
							Log.e("Isis Resource Release", "Error Releasing Resources, likely the Activity is not a subClass of IsisActivity");
						}
					}
				}
			} catch (Exception e) {}
		}

	@Override
	public void onDestroy() {
		releaseServices();
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (menuResource != null) {
			getMenuInflater().inflate(menuResource, menu);
			return true;
		}
		return false;
	}
}
