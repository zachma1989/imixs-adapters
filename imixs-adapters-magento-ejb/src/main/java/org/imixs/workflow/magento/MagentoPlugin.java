/*******************************************************************************
 *  Imixs Workflow 
 *  Copyright (C) 2001, 2011 Imixs Software Solutions GmbH,  
 *  http://www.imixs.com
 *  
 *  This program is free software; you can redistribute it and/or 
 *  modify it under the terms of the GNU General Public License 
 *  as published by the Free Software Foundation; either version 2 
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful, 
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 *  General Public License for more details.
 *  
 *  You can receive a copy of the GNU General Public
 *  License at http://www.gnu.org/licenses/gpl.html
 *  
 *  Project: 
 *  	http://www.imixs.org
 *  	http://java.net/projects/imixs-workflow
 *  
 *  Contributors:  
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika - Software Developer
 *******************************************************************************/

package org.imixs.workflow.magento;

import java.util.List;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.Plugin;
import org.imixs.workflow.WorkflowContext;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.jee.ejb.WorkflowService;
import org.imixs.workflow.plugins.jee.AbstractPlugin;

/**
 * This plugin provides methods to interact with a magento instance through the
 * magento rest api.
 * 
 * @author rsoika
 * 
 */
public class MagentoPlugin extends AbstractPlugin {

	public final static String MAGENTOSERVICE_NOT_BOUND = "MAGENTOSERVICE_NOT_BOUND";
	public final static String MAGENTO_STATUS_NOT_SYNCHRONIZED = "MAGENTO_STATUS_NOT_SYNCHRONIZED";
	public final static String ERROR_MESSAGE = "ERROR_MESSAGE";
	public final static int ACTIVITY_CREATE = 800; // create new order workitem
	public final static int ACTIVITY_UPDATE = 801; // update order workitem
	public final static int ACTIVITY_NOT_SYNCHRONIZED = 802; // processid not
																// equals
																// magento
																// status

	ItemCollection documentContext;

	// Basic Authentication
	String user = null;
	String password = null;

	private MagentoService magentoService = null;
	private WorkflowService workflowSerivice = null;

	private static Logger logger = Logger.getLogger(MagentoPlugin.class
			.getName());

	/**
	 * 
	 */
	@Override
	public void init(WorkflowContext actx) throws PluginException {
		super.init(actx);

		// check for an instance of WorkflowService
		if (actx instanceof WorkflowService) {
			// yes we are running in a WorkflowService EJB
			workflowSerivice = (WorkflowService) actx;
		}

		try {
			// lookup PropertyService
			InitialContext ictx = new InitialContext();
			Context ctx = (Context) ictx.lookup("java:comp/env");
			String jndiName = "ejb/MagentoService";
			magentoService = (MagentoService) ctx.lookup(jndiName);
		} catch (NamingException e) {
			throw new PluginException(MagentoPlugin.class.getSimpleName(),
					MAGENTOSERVICE_NOT_BOUND, "MagentoService not bound", e);
		}

	}

	/**
	 * This method lookups the order data and updates the magento properties of
	 * the workitem
	 * 
	 * <code>
	    txtMagentoCustomer=magento customer name (First- and Lastname)
		txtMagentoCustomerEmail = E-Mail address of customer
		txtMagentoOrderID = Order id of magento order
       </code>
	 */
	@Override
	public int run(ItemCollection workitem, ItemCollection documentActivity)
			throws PluginException {

		String sKey = workitem.getItemValueString("txtName");
		if (sKey.startsWith("magento:order:")) {

			// create custom magento fields
			workitem.replaceItemValue("txtMagentoOrderID",
					workitem.getItemValueString("m_entity_id"));
			List<ItemCollection> addresses = workitem
					.getItemValue("m_addresses");

			if (addresses != null && addresses.size() > 0) {
				ItemCollection address = addresses.get(0);
				workitem.replaceItemValue(
						"txtMagentoCustomer",
						address.getItemValueString("firstname") + " "
								+ address.getItemValueString("lastname"));
				workitem.replaceItemValue("txtMagentoCustomerEmail",
						address.getItemValueString("email"));
			}

		}

		return Plugin.PLUGIN_OK;
	}

	@Override
	public void close(int arg0) throws PluginException {

		// no op

	}

}
