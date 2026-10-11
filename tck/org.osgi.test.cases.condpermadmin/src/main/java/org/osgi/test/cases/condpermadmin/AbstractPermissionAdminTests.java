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

package org.osgi.test.cases.condpermadmin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.security.AllPermission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin;
import org.osgi.service.condpermadmin.ConditionalPermissionUpdate;
import org.osgi.service.permissionadmin.PermissionAdmin;
import org.osgi.service.permissionadmin.PermissionInfo;
import org.osgi.test.cases.condpermadmin.junit.ConditionalTestSupport;
import org.osgi.test.common.annotation.InjectBundleContext;

public abstract class AbstractPermissionAdminTests {

	@InjectBundleContext
	protected BundleContext		context;

	protected ConditionalPermissionAdmin condPermAdmin;
	protected PermissionAdmin permAdmin;
	private Collection<ServiceReference< ? >>	serviceRefs;

	@BeforeEach
	public void setUp() throws Exception {
		serviceRefs = new ArrayList<>();
		// get condpermadmin
		condPermAdmin = getService(ConditionalPermissionAdmin.class);
		// get permadmin
		permAdmin = getService(PermissionAdmin.class);
		
	    // make sure this bundle has all permissions
	    permAdmin.setPermissions(context.getBundle().getLocation(), new PermissionInfo[] {new PermissionInfo("(" + AllPermission.class.getName() +")")});
	    ConditionalTestSupport.grantAllPermission(permAdmin, context.getBundles());
	}

	@AfterEach
	public void tearDown() throws Exception {
		// clear any permissions set
		ConditionalPermissionUpdate update = condPermAdmin.newConditionalPermissionUpdate();
	    update.getConditionalPermissionInfos().clear();
	    update.commit();
	    permAdmin.setDefaultPermissions(null);

	    String[] locations = permAdmin.getLocations();
	    if (locations != null)
	    	for (int i = 0; i < locations.length; i++)
	    		permAdmin.setPermissions(locations[i], null);

		for (Iterator<ServiceReference< ? >> iRefs = serviceRefs
				.iterator(); iRefs.hasNext();) {
	    	context.ungetService(iRefs.next());
	    }
		serviceRefs = null;

	    condPermAdmin = null;
	    permAdmin = null;
	}

	protected Bundle installBundle(String bundle, boolean start) throws BundleException, IOException {
		URL entry = context.getBundle().getEntry(bundle);
		assertThat(entry).as("Can not find bundle: " + bundle).isNotNull();
		// the real context: the osgi-test context uninstalls its bundles after
		// each test, and testcond.jar is a framework extension
		Bundle b= context.getBundle().getBundleContext().installBundle(entry.toExternalForm(), entry.openStream());
	    // make sure the test bundles do not have any location permissions set; SimplePermissionPolicy sets these
	    permAdmin.setPermissions(b.getLocation(), null);
		if (start)
			b.start();
		return b;
	}

	public <T> T getService(Class<T> clazz) {
		ServiceReference<T> ref = context.getServiceReference(clazz);
        assertThat(ref).as("No service found: " + clazz).isNotNull();
		T service = context.getService(ref);
        assertThat(service).as("No service found: " + clazz).isNotNull();
        serviceRefs.add(ref);
        return service;
    }
}
