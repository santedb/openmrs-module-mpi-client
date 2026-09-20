/**
 * Original Copyright (c) 2014-2018 Justin Fyfe (Fyfe Software Inc.) 
 * Copyright (c) 2018 SanteDB Community
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you 
 * may not use this file except in compliance with the License. You may 
 * obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations under 
 * the License.
 */
package org.openmrs.module.santedb.mpiclient.extension.html;

import org.openmrs.api.context.Context;
import org.openmrs.module.web.extension.LinkExt;

/**
 * Link to the end of OpenMRS which allows access to HIE data
 * @author Justin
 *
 */
public class MpiClientLink extends LinkExt {

	/**
	 * Get the label of the extension
	 */
	@Override
	public String getLabel() {
		return Context.getMessageSourceService().getMessage("santedb-mpiclient.hiePortlet.linkText");
	}

	/**
	 * Get the required priv.
	 */
	@Override
	public String getRequiredPrivilege() {
		return "Add Patients";
	}

	/**
	 * Get the url that this portlet will point to
	 */
	@Override
	public String getUrl() {
		return "module/santedb-mpiclient/mpiFindPatient.form";
	}

}