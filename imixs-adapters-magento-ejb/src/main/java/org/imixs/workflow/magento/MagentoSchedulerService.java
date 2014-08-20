/*******************************************************************************
 *  Imixs IX Workflow Technology
 *  Copyright (C) 2001, 2008 Imixs Software Solutions GmbH,  
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
 *  Contributors:  
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika
 *******************************************************************************/
package org.imixs.workflow.magento;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RunAs;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.exceptions.InvalidAccessException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.exceptions.ProcessingErrorException;
import org.imixs.workflow.jee.ejb.WorkflowService;

/**
 * Magento - Scheduler
 * 
 * This is the implementation of a scheduler service. The EJB implementation can
 * be used as a Timer Service to process scheduled activities.
 * 
 * 
 * The TimerService can be started using the method start(). The Methods
 * findTimerDescription and findAllTimerDescriptions are used to lookup enabled
 * and running service instances.
 * 
 * Each Method expects or generates a TimerDescription Object. This object is an
 * instance of a ItemCollection. To create a new timer the ItemCollection should
 * contain the following attributes:
 * 
 * datstart - Date Object
 * 
 * datstop - Date Object
 * 
 * numInterval - Integer Object (interval in seconds)
 * 
 * id - String - unique identifier for the schedule Service.
 * 
 * $modelversion - String - identifies the model which schould be managed by the
 * service
 * 
 * the following additional attributes are generated by the finder methods and
 * can be used by an application to verfiy the status of a running instance:
 * 
 * nextTimeout - Next Timeout - pint of time when the service will be scheduled
 * 
 * timeRemaining - Timeout in milliseconds
 * 
 * statusmessage - text message
 * 
 * 
 * 
 ******** Magento Import *************************************
 * 
 * The timer service starte the processImport method. this method is repsonsible
 * for the import of orders from magento. To map the status defined in magento
 * with the status defined in process modelel the two properties
 * 'txtModelVersion' are defined. 'txtOrderStatusMapping'
 * 
 * txtModelVersion - defines the $modelversion to be defined for a new imported
 * workitem.
 * 
 * txtOrderStatusMapping - holds a map of magento status keywords and the
 * corresponding ProcessID in the Imixs Model. e.g.
 * 
 * <code>
 *   pending=1010
 *   processing=1050
 * </code>
 * 
 * This example defines the a new order with the magento status 'pending' should
 * be mapped to a ProcessID 1010 in the imixs workflow model.
 * 
 * All new imported Workitems will be automatically processed with the
 * ActivityID 800.
 * 
 * If for a magento order an imixs workitem still exits but the item is not
 * equal then the Service will process the workiem with the ActivityID 801.
 * 
 * If for a magento order an imixs workitem still exits but the status is not
 * equal to the txtOrderStatusMapping then the Service will change the
 * $ProcessID to the corresponding ProcessID and process the workiem with the
 * ActivityID 802.
 * 
 * NOTE: It is important that in every workflow state defined by the
 * txtOrderStatusMapping the ActiviyEntities 800 and 801 are defined! If not a
 * WorkflowException will be thrown during the import process.
 * 
 * 
 * The Magento ID will be stored in the proeprty 'txtName'. As this property
 * need to be unique the method. createMagentoID of the MagentoPlugin will be
 * used to generate an unique id.
 * 
 * 
 * @author rsoika
 * 
 */
@DeclareRoles({ "org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@Stateless
@RunAs("org.imixs.ACCESSLEVEL.MANAGERACCESS")
@Local
public class MagentoSchedulerService {

	// @PersistenceContext(unitName = "org.imixs.workflow.jee.jpa")
	// private EntityManager manager;

	private Date startDate, endDate;
	private long interval;
	private String id;
	private ItemCollection configuration = null;
	private int workitemsImported;
	private int workitemsUpdated;
	private int workitemsFailed;

	@Resource
	SessionContext ctx;

	@EJB
	WorkflowService workflowService;

	// @EJB
	// EntityService entityService;

	@EJB
	MagentoService magentoService;
	
	@EJB 
	MagentoCache magentoCache;

	@Resource
	javax.ejb.TimerService timerService;

	private static Logger logger = Logger
			.getLogger(MagentoSchedulerService.class.getName());

	/**
	 * This method loads the configuration from an entity with type=ENTITY_TYPE
	 */
	public ItemCollection loadConfiguration() {
		configuration = magentoService.loadConfiguration();
		if (configuration == null) {
			try {
				// create an empty entity with type and with start and stop
				// default values
				configuration = new ItemCollection();
				Calendar cal = Calendar.getInstance();
				configuration.replaceItemValue("datStart", cal.getTime());
				configuration.replaceItemValue("datStop", cal.getTime());
				configuration.replaceItemValue("type",
						MagentoService.ENTITY_TYPE);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		updateTimerDetails();
		return configuration;
	}

	public ItemCollection saveConfiguration(ItemCollection aconfig)
			throws Exception {
		aconfig.replaceItemValue("type", MagentoService.ENTITY_TYPE);

		aconfig.removeItem("nextTimeout");
		aconfig.removeItem("timeRemaining");
		configuration = workflowService.getEntityService().save(aconfig);
		return configuration;
	}

	/**
	 * This Method starts a new TimerService.
	 * 
	 * The method loads configuration from a ItemCollection (timerdescription)
	 * with the following informations:
	 * 
	 * datstart - Date Object
	 * 
	 * datstop - Date Object
	 * 
	 * numInterval - Integer Object (interval in seconds)
	 * 
	 * id - String - unique identifier for the schedule Service.
	 * 
	 * The param 'id' should contain a unique identifier (e.g. the EJB Name) as
	 * only one scheduled Workflow should run inside a WorkflowInstance. If a
	 * timer with the id is already running the method stops this timer object
	 * first and reschedules the timer.
	 * 
	 * The method throws an exception if the timerdescription contains invalid
	 * attributes or values.
	 */
	public ItemCollection start() throws Exception {

		configuration = loadConfiguration();
		id = configuration.getItemValueString("$uniqueid");

		// startdatum und enddatum manuell festlegen
		Calendar cal = Calendar.getInstance();
		startDate = cal.getTime();
		cal.add(Calendar.YEAR, 10);
		endDate = cal.getTime();
		configuration.replaceItemValue("datstart", startDate);
		configuration.replaceItemValue("datstop", endDate);

		// compute intervall
		int hours = configuration.getItemValueInteger("hours");
		int minutes = configuration.getItemValueInteger("minutes");

		interval = (hours * 60 + minutes) * 60 * 1000;

		configuration.replaceItemValue("numInterval", new Long(interval));
		// try to cancel an existing timer for this workflowinstance
		Timer timer = this.findTimer(id);
		if (timer != null)
			timer.cancel();

		// set statusmessage
		Calendar calNow = Calendar.getInstance();
		String msg = "Started: " + calNow.getTime() + " by "
				+ ctx.getCallerPrincipal().getName();
		configuration.replaceItemValue("statusmessage", msg);
		timer = timerService.createTimer(startDate, interval, configuration);
		System.out.println("[ImportSchedulerService] started: " + id);

		// load current timer details
		updateTimerDetails();

		configuration = saveConfiguration(configuration);
		return configuration;
	}

	/**
	 * Cancels a running timer instance. After cancel a timer the corresponding
	 * timerDescripton (ItemCollection) is no longer valid
	 * 
	 */
	public ItemCollection stop() throws Exception {
		configuration = loadConfiguration();
		id = configuration.getItemValueString("$uniqueid");

		boolean found = false;
		while (this.findTimer(id) != null) {
			this.findTimer(id).cancel();
			found = true;
		}
		if (found) {

			Calendar calNow = Calendar.getInstance();
			SimpleDateFormat dateFormatDE = new SimpleDateFormat(
					"dd.MM.yy hh:mm:ss");

			String msg = "stopped at " + dateFormatDE.format(calNow.getTime())
					+ " by " + ctx.getCallerPrincipal().getName();
			configuration.replaceItemValue("statusmessage", msg);

			logger.info("[WorkflowSchedulerService] "
					+ configuration.getItemValueString("txtName")
					+ " stopped: " + id);
		} else {
			configuration.replaceItemValue("statusmessage", "");
		}

		updateTimerDetails();
		configuration = saveConfiguration(configuration);

		return configuration;
	}

	/**
	 * This method returns a timer for a corresponding id if such a timer object
	 * exists.
	 * 
	 * @param id
	 * @return Timer
	 * @throws Exception
	 */
	private Timer findTimer(String id) {
		for (Object obj : timerService.getTimers()) {
			Timer timer = (javax.ejb.Timer) obj;
			if (timer.getInfo() instanceof ItemCollection) {
				ItemCollection adescription = (ItemCollection) timer.getInfo();
				if (id.equals(adescription.getItemValueString("$uniqueid"))) {
					return timer;
				}
			}
		}
		return null;
	}

	private void updateTimerDetails() {
		if (configuration == null)
			return;
		id = configuration.getItemValueString("$uniqueid");
		Timer timer = this.findTimer(id);
		if (timer != null) {
			// load current timer details
			configuration.replaceItemValue("nextTimeout",
					timer.getNextTimeout());
			configuration.replaceItemValue("timeRemaining",
					timer.getTimeRemaining());
		} else {
			configuration.replaceItemValue("nextTimeout", "");
			configuration.replaceItemValue("timeRemaining", "");

		}
	}

	/**
	 * This is the method which processes the timeout event depending on the
	 * running timer settings.
	 * 
	 * The method imports all orders.
	 * 
	 * The method also makes a flush on the MagentoCache EJB. 
	 * 
	 * @param timer
	 */
	@Timeout
	public void processImport(javax.ejb.Timer timer) {
		String sTimerID = null;
		workitemsImported = 0;
		workitemsUpdated = 0;
		workitemsFailed = 0;

		// Startzeit ermitteln
		long lProfiler = System.currentTimeMillis();

		logger.info("[MagentoSchedulerService] processing import....");

		// flush cache
		magentoCache.flush();
		
		try {
			// configuration = (ItemCollection) timer.getInfo();
			configuration = loadConfiguration();
			sTimerID = configuration.getItemValueString("$uniqueid");

			importOrders();

			configuration.replaceItemValue("errormessage", "");
			configuration.replaceItemValue("datLastRun", new Date());
			configuration.replaceItemValue("numWorkItemsImported",
					workitemsImported);
			configuration.replaceItemValue("numWorkItemsUpdated",
					workitemsUpdated);
			configuration.replaceItemValue("numWorkItemsFailed",
					workitemsFailed);

		} catch (PluginException e) {
			e.printStackTrace();
			// stop timer!
			timer.cancel();
			System.out
					.println("[ImportSchedulerService] Timeout sevice stopped: "
							+ sTimerID);
			configuration.replaceItemValue("errormessage", e.toString());

		}

		// Save statistic in configuration
		try {
			configuration = this.saveConfiguration(configuration);
		} catch (Exception e2) {
			e2.printStackTrace();

		}

		logger.info("[ImportSchedulerService]  finished successfull: "
				+ ((System.currentTimeMillis()) - lProfiler) + " ms");

		/*
		 * Check if Timer should be canceld now?
		 */
		if (endDate != null) {
			Calendar calNow = Calendar.getInstance();

			if (calNow.getTime().after(endDate)) {
				timer.cancel();
				System.out
						.println("[ImportSchedulerService] Timeout sevice stopped: "
								+ sTimerID);
			}
		}
	}

	/**
	 * Liefert die trefferliste nach einem Suchbegriff für Artikel zurück
	 * 
	 * Wenn der suchbegriff eine zahl ist, dann wird das ERgebnis nach der ID
	 * sortiert. Annderfalls alphabetisch nach dem namen.
	 * 
	 * @param searchPhrase
	 * @return
	 */
	public List<ItemCollection> searchArtikel(String searchPhrase) {

		List<ItemCollection> result = new ArrayList<ItemCollection>();

		return result;

	}

	/**
	 * This method imports all orders or update existing workitems. The method
	 * imports orders for all states defined in the configuration property
	 * 'txtOrderStatusMapping'
	 * 
	 * If no workitem exits the method will create a new one with the
	 * $ModelVersion defined by the configruation property 'txtModelVersion'.
	 * THe new Workitem will be process with the ActivityID 800
	 * 
	 * If the workitem still exits but the state did not match the $ProcessID of
	 * the workitem will be changed and th workitem will be processed withe
	 * ActivityID 801.
	 * 
	 * 
	 * The method implements a paging meachanism because magento returns maximum
	 * 100 order per request.
	 * 
	 * @throws PluginException
	 */
	@SuppressWarnings("unchecked")
	public void importOrders() throws PluginException {
		int iProcessID = -1;
		String sMagentoStatus = null;

		List<String> orderStatusMapping = configuration
				.getItemValue("txtOrderStatusMapping");
		String orderModelVersion = configuration
				.getItemValueString("txtModelVersion");

		// find processid....
		// format: pending=1000
		for (String mapping : orderStatusMapping) {
			// read mapping string
			try {
				int pos = mapping.indexOf("=");
				sMagentoStatus = mapping.substring(0, pos);
				String sProcessid = mapping.substring(pos + 1);
				iProcessID = new Integer(sProcessid);
			} catch (Exception e) {
				logger.warning("[MagentoSchedulerService] wrong order status mapping in '"
						+ mapping + "' - check configuration");
				continue;
			}

			// fetch orders by status.....
			// we need to implement a paging here, because magento deliveres
			// only max of 100 entries.
			boolean hasMore = true;
			int page = 1;
			int limit = 100;
			String sLastEntityID = null;

			while (hasMore) {
				logger.info("[MagentoSchedulerSerivce] read "
						+ " orders (status=" + sMagentoStatus + ") - limit="
						+ +limit + " page=" + page);

				List<ItemCollection> orders = magentoService.getOrders(
						sMagentoStatus, page, limit);

				if (orders.size() == 0) {
					logger.fine("[MagentoSchedulerSerivce] no more orders found ");
					break;
				} else {
					// test first entry to verify if this oder junk was already
					// read
					// before (magento delivers event if page is > max orders!)
					String sEntity_id = orders.get(0).getItemValueString(
							"entity_id");
					if (sEntity_id.equals(sLastEntityID)) {
						// max enties read! we can leave here...
						hasMore = false;
						logger.info("[MagentoSchedulerSerivce] max orders read ");
						break;
					}
					sLastEntityID = sEntity_id;
				}

				logger.info("[MagentoSchedulerSerivce] " + orders.size()
						+ " pending orders found. Start processing....");

				// process order list
				processOrderList(orders, orderModelVersion, iProcessID);

				// continue with next page!
				page++;
			}
		}

	}

	/**
	 * This method processes the orders read form magento..
	 * 
	 * @param orders
	 */
	private void processOrderList(List<ItemCollection> orders,
			String orderModelVersion, int iProcessID) {
		// verify orders....
		for (ItemCollection order : orders) {

			try {
				String sMagentoKey = magentoService.getOrderID(order);

				// check if workitem exits....
				ItemCollection workitem = magentoService
						.findWorkitemByOrder(order);

				if (workitem == null) {
					logger.fine("[MagentoSchedulerService] create new workitem: '"
							+ sMagentoKey + "'");
					workitem = new ItemCollection();
					workitem.replaceItemValue("type", "workitem");
					workitem.replaceItemValue("txtName", sMagentoKey);
					workitem.replaceItemValue(WorkflowService.MODELVERSION,
							orderModelVersion);
					workitem.replaceItemValue("$ProcessID", new Integer(
							iProcessID));
					workitem.replaceItemValue("txtMagentoError","");


					// process activityId = 800
					workitem.replaceItemValue("$ActivityID", new Integer(
							MagentoPlugin.ACTIVITY_CREATE));

					// transfer order items
					magentoService.addMagentoEntity(workitem, order);

					// workflowService.processWorkItem(workitem);
					ctx.getBusinessObject(MagentoSchedulerService.class)
							.processSingleWorkitem(workitem);
					workitemsImported++;

				} else {

					logger.fine("[MagentoSchedulerService] Workitem for order '"
							+ sMagentoKey
							+ "' already exists ("
							+ workitem
									.getItemValueString(WorkflowService.UNIQUEID)
							+ ")");
					// check if status has changed!
					int iCurrentProcessId = workitem
							.getItemValueInteger("$ProcessID");
					if (iCurrentProcessId != iProcessID) {
						logger.warning("[MagentoSchedulerService] update workitem: '"
								+ sMagentoKey + "' - $ProcessID is not matching Magento Status");
						// processID not equals magento state
						workitem.replaceItemValue("$ActivityID", new Integer(
								MagentoPlugin.ACTIVITY_NOT_SYNCHRONIZED));
						workitem.replaceItemValue("txtMagentoError",MagentoPlugin.MAGENTO_STATUS_NOT_SYNCHRONIZED);

						// transfer order items
						magentoService.addMagentoEntity(workitem, order);

						// workflowService.processWorkItem(workitem);
						ctx.getBusinessObject(MagentoSchedulerService.class)
								.processSingleWorkitem(workitem);
						workitemsUpdated++;
					} else {
						// check if order details have changed
						if (! magentoService.isWorkitemEqualsToMagentoEntity(workitem, order)) {
							magentoService.addMagentoEntity(workitem, order);
							workitem.replaceItemValue("$ActivityID",
									new Integer(MagentoPlugin.ACTIVITY_UPDATE));
							workitem.replaceItemValue("txtMagentoError","");

							// transfer order items
							ctx.getBusinessObject(MagentoSchedulerService.class)
									.processSingleWorkitem(workitem);
							workitemsUpdated++;
						}

					}
				}

			} catch (InvalidAccessException ew) {
				workitemsFailed++;
				logger.warning("[MagentoSchedulerService] failed to import order: "
						+ ew.getMessage());
			} catch (PluginException e) {
				workitemsFailed++;
				logger.warning("[MagentoSchedulerService] failed to import order: "
						+ e.getMessage());
			} catch (IllegalStateException e) {
				workitemsFailed++;
				logger.warning("[MagentoSchedulerService] failed to import order: "
						+ e.getMessage());
			}
		}
	}

	/**
	 * This method process a single workIten in a new transaction. The method is
	 * called by processWorklist()
	 * 
	 * @param aWorkitem
	 * @throws PluginException
	 * @throws ProcessingErrorException
	 * @throws AccessDeniedException
	 */
	@TransactionAttribute(value = TransactionAttributeType.REQUIRES_NEW)
	public void processSingleWorkitem(ItemCollection aWorkitem)
			throws AccessDeniedException, ProcessingErrorException,
			PluginException {
		workflowService.processWorkItem(aWorkitem);
	}

}
