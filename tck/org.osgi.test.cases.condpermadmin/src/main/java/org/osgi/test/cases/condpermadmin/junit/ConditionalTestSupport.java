/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0 
 *******************************************************************************/

package org.osgi.test.cases.condpermadmin.junit;

import static org.assertj.core.api.Assertions.fail;

import java.net.URL;
import java.security.AllPermission;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.permissionadmin.PermissionAdmin;
import org.osgi.service.permissionadmin.PermissionInfo;
import org.osgi.test.common.annotation.InjectBundleContext;

/**
 * The helpers ConditionalTestControl used from DefaultTestBundleControl,
 * unchanged: a fetched service remembers its reference, so it can be released
 * by the service object.
 */
public abstract class ConditionalTestSupport {
	@InjectBundleContext
	protected BundleContext								context;


	private final Map<Object,ServiceReference< ? >>	fetchedServices	= new HashMap<>();

	/**
	 * As DefaultTestBundleControl: installed from the bundle entry URL through
	 * the real BundleContext, so not uninstalled automatically at the end of a
	 * test as the osgi-test context and BundleInstaller do; testcond.jar is a
	 * framework extension, which cannot be installed again once uninstalled.
	 */
	public Bundle installBundle(String bundleName) throws Exception {
		return installBundle(bundleName, true);
	}

	public Bundle installBundle(String bundleName, boolean start)
			throws Exception {
		URL entry = context.getBundle().getEntry(bundleName);
		Bundle b = context.getBundle()
				.getBundleContext()
				.installBundle(entry.toExternalForm(), entry.openStream());
		if (start) {
			b.start();
		}
		return b;
	}

	public boolean serviceAvailable(Class< ? > clazz) {
		return context.getServiceReference(clazz.getName()) != null;
	}

	public <S> S getService(Class<S> clazz) {
		try {
			return getService(clazz, null);
		} catch (InvalidSyntaxException e) {
			/* null can not give an exception of this type */
		}
		return null;
	}

	public <S> S getService(Class<S> clazz, String filter)
			throws InvalidSyntaxException {
		@SuppressWarnings("unchecked")
		ServiceReference<S>[] refs = (ServiceReference<S>[]) context
				.getServiceReferences(clazz.getName(), filter);
		if (refs == null) {
			fail("Can't get service reference for " + clazz.getName() + filter);
		}
		ServiceReference<S> chosenRef = refs[0];
		/* highest ranking, then lowest service id: the ServiceReference order */
		for (int i = 1; i < refs.length; i++) {
			if (refs[i].compareTo(chosenRef) > 0) {
				chosenRef = refs[i];
			}
		}
		S service = context.getService(chosenRef);
		if (service == null) {
			fail("Can't get service for " + clazz.getName() + filter);
		}
		synchronized (fetchedServices) {
			fetchedServices.put(service, chosenRef);
		}
		return service;
	}

	public void ungetService(Object service) {
		ServiceReference< ? > ref;
		synchronized (fetchedServices) {
			ref = fetchedServices.remove(service);
		}
		if (ref == null) {
			fail("trying to unget a service which is not currently bound");
		}
		context.ungetService(ref);
	}

	public void ungetAllServices() {
		ServiceReference< ? >[] refs;
		synchronized (fetchedServices) {
			refs = fetchedServices.values()
					.toArray(new ServiceReference[fetchedServices.size()]);
			fetchedServices.clear();
		}
		for (int l = refs.length, i = 0; i < l; i++) {
			context.ungetService(refs[i]);
		}
	}

	public static void failException(String message,
			Class< ? > expectedExceptionClass) {
		fail(message + " expected:[" + expectedExceptionClass.getName()
				+ "] and got nothing");
	}

	public static void assertException(String message,
			Class< ? extends Throwable> want, Throwable got) {
		String formatted = "";
		if (message != null) {
			formatted = message + " ";
		}
		if (!want.isInstance(got)) {
			fail(formatted + "expected:[" + want.getName() + "] but was:["
					+ got.getClass().getName() + "]", got);
		}
	}

	public static String arrayToString(Object[] array) {
		return arrayToString(array, false);
	}

	public static String arrayToString(Object[] array, boolean sort) {
		StringBuffer buf = new StringBuffer();
		if (sort) {
			Arrays.sort(array);
		}
		if (array != null) {
			buf.append("{ ");
			for (int i = 0; i < array.length; i++) {
				buf.append(array[i]);
				if (i + 1 < array.length) {
					buf.append(", ");
				}
			}
			buf.append(" }");
		} else {
			buf.append("null");
		}
		return buf.toString();
	}
	/**
	 * The JUnit 5 tester and the test infrastructure run as bundles in
	 * the framework and are on the stack of every permission check: keep them
	 * at AllPermission, as the classic tester on the class path was.
	 */
	public static void grantAllPermission(PermissionAdmin permissionAdmin,
			Bundle[] infrastructure) {
		for (Bundle b : infrastructure) {
			if (b.getBundleId() != 0) {
				permissionAdmin.setPermissions(b.getLocation(),
						new PermissionInfo[] {new PermissionInfo(
								"(" + AllPermission.class.getName() + ")")});
			}
		}
	}
}
