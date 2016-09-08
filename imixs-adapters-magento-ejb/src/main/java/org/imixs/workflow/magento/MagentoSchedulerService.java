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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RunAs;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.ScheduleExpression;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.exceptions.ModelException;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.exceptions.ProcessingErrorException;
import org.imixs.workflow.jee.ejb.EntityService;
import org.imixs.workflow.jee.ejb.WorkflowService;
import org.imixs.workflow.xml.XMLItemCollection;
import org.imixs.workflow.xml.XMLItemCollectionAdapter;

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
 * equal then the Service will update the magento data and process the workiem
 * with also the ActivityID CTIVITY_MAGENTO_UPDATE=800.
 * 
 * 
 * NOTE: It is important that in every workflow state defined by the
 * txtOrderStatusMapping the ActiviyEntity ACTIVITY_MAGENTO_UPDATE=800. If not a
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
@Stateless
@LocalBean
@DeclareRoles({ "org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@RunAs("org.imixs.ACCESSLEVEL.MANAGERACCESS")
public class MagentoSchedulerService {

	private Date endDate;
	private int workitemsImported;
	private int workitemsUpdated;
	private int workitemsFailed;
	private int magentoOrdersTotal;

	public final static String IMPORT_ERROR = "IMPORT_ERROR";

	@Resource
	SessionContext ctx;

	@EJB
	WorkflowService workflowService;

	@EJB
	MagentoService magentoService;

	@EJB
	MagentoCache magentoCache;

	@Resource
	javax.ejb.TimerService timerService;

	private static Logger logger = Logger
			.getLogger(MagentoSchedulerService.class.getName());

	/***
	 * retruns a list of all existing Magento Shop Configurations
	 * 
	 * @return
	 */
	public List<ItemCollection> findAllConfigurations() {
		// load all configurations...
		return magentoService.findAllConfigurations();
	}

	/**
	 * This method saves the timer configuration. The method ensures that the
	 * following properties are set to default.
	 * <ul>
	 * <li>type</li>
	 * <li>txtName</li>
	 * <li>$writeAccess</li>
	 * <li>$readAccess</li>
	 * </ul>
	 * 
	 * The method also updates the timer details of a running timer.
	 * 
	 * @return
	 * @throws AccessDeniedException
	 */
	public ItemCollection saveConfiguration(ItemCollection configItemCollection)
			throws AccessDeniedException {
		// update write and read access
		configItemCollection.replaceItemValue("type", MagentoService.TYPE);
		// configItemCollection.replaceItemValue("txtName", NAME);
		configItemCollection.replaceItemValue("$writeAccess",
				"org.imixs.ACCESSLEVEL.MANAGERACCESS");
		configItemCollection.replaceItemValue("$readAccess",
				"org.imixs.ACCESSLEVEL.MANAGERACCESS");

		// configItemCollection.replaceItemValue("$writeAccess", "");
		// configItemCollection.replaceItemValue("$readAccess", "");

		configItemCollection = updateTimerDetails(configItemCollection, false);
		// save entity
		configItemCollection = workflowService.getEntityService().save(
				configItemCollection);

		return configItemCollection;
	}

	/**
	 * This Method starts the TimerService.
	 * 
	 * The Timer can be started based on a Calendar setting stored in the
	 * property txtConfiguration, or by interval based on the properties
	 * datStart, datStop, numIntervall.
	 * 
	 * 
	 * The method loads the configuration entity and evaluates the timer
	 * configuration. THe $UniqueID of the configuration entity is the id of the
	 * timer to be controlled.
	 * 
	 * $uniqueid - String - identifier for the Timer Service.
	 * 
	 * txtConfiguration - calendarBasedTimer configuration
	 * 
	 * datstart - Date Object
	 * 
	 * datstop - Date Object
	 * 
	 * numInterval - Integer Object (interval in seconds)
	 * 
	 * 
	 * The method throws an exception if the configuration entity contains
	 * invalid attributes or values.
	 * 
	 * After the timer was started the configuration is updated with the latest
	 * statusmessage
	 * 
	 * The method returns the current configuration
	 * 
	 * @throws AccessDeniedException
	 * @throws ParseException
	 */
	public ItemCollection start(ItemCollection configItemCollection)
			throws AccessDeniedException, ParseException {
		Timer timer = null;
		if (configItemCollection == null)
			return null;

		String id = configItemCollection.getItemValueString("$uniqueid");

		// try to cancel an existing timer for this workflowinstance
		while (this.findTimer(id) != null) {
			this.findTimer(id).cancel();
		}

		String sConfiguation = configItemCollection
				.getItemValueString("txtConfiguration");

		if (!sConfiguation.isEmpty()) {
			// New timer will be started on calendar confiugration
			timer = createTimerOnCalendar(configItemCollection);
		} else {
			// update the interval based on hour/minute configuration
			int hours = configItemCollection.getItemValueInteger("hours");
			int minutes = configItemCollection.getItemValueInteger("minutes");
			long interval = (hours * 60 + minutes) * 60 * 1000;
			configItemCollection.replaceItemValue("numInterval", new Long(
					interval));

			timer = createTimerOnInterval(configItemCollection);
		}

		// start and set statusmessage
		if (timer != null) {

			Calendar calNow = Calendar.getInstance();
			SimpleDateFormat dateFormatDE = new SimpleDateFormat(
					"dd.MM.yy hh:mm:ss");
			String msg = "started at " + dateFormatDE.format(calNow.getTime())
					+ " by " + ctx.getCallerPrincipal().getName();
			configItemCollection.replaceItemValue("statusmessage", msg);

			if (timer.isCalendarTimer()) {
				configItemCollection.replaceItemValue("Schedule", timer
						.getSchedule().toString());
			} else {
				configItemCollection.replaceItemValue("Schedule", "");

			}
			logger.info("[MagentoSchedulerService] "
					+ configItemCollection.getItemValueString("txtName")
					+ " started: " + id);
		}
		configItemCollection.replaceItemValue("errormessage", "");
		configItemCollection = saveConfiguration(configItemCollection);

		// clear Cache!
		magentoService.clearCache();

		return configItemCollection;
	}

	/**
	 * Cancels a running timer instance. After cancel a timer the corresponding
	 * timerDescripton (ItemCollection) is no longer valid
	 * 
	 */
	public ItemCollection stop(ItemCollection config) throws Exception {
		String id = config.getItemValueString(EntityService.UNIQUEID);
		boolean found = false;
		while (this.findTimer(id) != null) {
			this.findTimer(id).cancel();
			found = true;
		}
		if (found) {
			ItemCollection configuration = workflowService.getWorkItem(id);
			Calendar calNow = Calendar.getInstance();
			SimpleDateFormat dateFormatDE = new SimpleDateFormat(
					"dd.MM.yy hh:mm:ss");

			String msg = "stopped at " + dateFormatDE.format(calNow.getTime())
					+ " by " + ctx.getCallerPrincipal().getName();
			configuration.replaceItemValue("statusmessage", msg);

			logger.info("[MagentoSchedulerService] "
					+ configuration.getItemValueString("txtName")
					+ " stopped: " + id);

			configuration.removeItem("nextTimeout");
			configuration.removeItem("timeRemaining");

			configuration = saveConfiguration(configuration);
			return configuration;
		}
		return config;
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
			
			if (timer.getInfo() instanceof XMLItemCollection) {
				XMLItemCollection xmlItemCollection = (XMLItemCollection) timer.getInfo();
				ItemCollection adescription = XMLItemCollectionAdapter.getItemCollection(xmlItemCollection);
				if (id.equals(adescription.getItemValueString("$uniqueid"))) {
					return timer;
				}
			}
		}
		return null;
	}

	/**
	 * Update the timer details of a running timer service. The method updates
	 * the properties netxtTimeout and timeRemaining and store them into the
	 * timer configuration.
	 * 
	 * @param configuration
	 * @param reload
	 *            - if true the confiugration will be reloaded from the database
	 */
	public ItemCollection updateTimerDetails(ItemCollection configuration,
			boolean reload) {
		if (configuration == null)
			return configuration;
		String id = configuration.getItemValueString("$uniqueid");

		// reload from database...
		if (reload) {
			configuration = workflowService.getEntityService().load(id);
		}

		Timer timer;
		try {
			timer = this.findTimer(id);

			if (timer != null) {
				// load current timer details
				configuration.replaceItemValue("nextTimeout",
						timer.getNextTimeout());
				configuration.replaceItemValue("timeRemaining",
						timer.getTimeRemaining());
			} else {
				configuration.removeItem("nextTimeout");
				configuration.removeItem("timeRemaining");

			}
		} catch (Exception e) {
			logger.warning("[MagentoSchedulerService] unable to updateTimerDetails: "
					+ e.getMessage());
			configuration.removeItem("nextTimeout");
			configuration.removeItem("timeRemaining");

		}
		return configuration;
	}

	/**
	 * This is the method which processes the timeout event depending on the
	 * running timer settings.
	 * 
	 * For each defined Shop Configuration the method imports all orders.
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
		magentoOrdersTotal = 0;

		// Startzeit ermitteln
		long lProfiler = System.currentTimeMillis();

		logger.info("[MagentoSchedulerService] processing import....");

		// reset clients
		magentoService.reset();
		// flush cache
		// magentoCache.flush();
		// issue #17 - clear cache!
		magentoCache.clearCache();

		// load configuration...
		
		XMLItemCollection xmlItemCollection = (XMLItemCollection) timer.getInfo();
		ItemCollection configuration = XMLItemCollectionAdapter.getItemCollection(xmlItemCollection);
		sTimerID = configuration.getItemValueString(EntityService.UNIQUEID);
		configuration = workflowService.getEntityService().load(sTimerID);
		try {
			importOrders(configuration);

			configuration.replaceItemValue("errormessage", "");
			configuration.replaceItemValue("datLastRun", new Date());
			configuration.replaceItemValue("numWorkItemsImported",
					workitemsImported);
			configuration.replaceItemValue("numWorkItemsUpdated",
					workitemsUpdated);
			configuration.replaceItemValue("numWorkItemsFailed",
					workitemsFailed);

			configuration
					.replaceItemValue("numOrdersTotal", magentoOrdersTotal);

		} catch (MagentoException e) {
			// in case of an exception we did not cancel the Timer service
			if (logger.isLoggable(Level.FINE)) {
				e.printStackTrace();
			}
			logger.severe("[MagentoSchedulerService] importOrders failed for: "
					+ sTimerID + " Error=" + e.getMessage());
			configuration.replaceItemValue("errormessage", e.getMessage());
			magentoService.reset();
		}

		// Save statistic in configuration
		try {
			configuration = this.saveConfiguration(configuration);
		} catch (Exception e2) {
			e2.printStackTrace();

		}

		logger.info("[MagentoSchedulerService] import finished successfull: "
				+ ((System.currentTimeMillis()) - lProfiler) + " ms");

		logger.info("[MagentoSchedulerService] " + magentoOrdersTotal
				+ " magento orders verified");
		logger.info("[MagentoSchedulerService] " + workitemsImported
				+ " workitems created");
		logger.info("[MagentoSchedulerService] " + workitemsUpdated
				+ " workitems updated");
		logger.info("[MagentoSchedulerService] " + workitemsFailed + " errors");

		// reset clients
		magentoService.reset();
		// issue #17 - clear cache!
		magentoCache.clearCache();

		/*
		 * Check if Timer should be canceld now?
		 */
		if (endDate != null) {
			Calendar calNow = Calendar.getInstance();

			if (calNow.getTime().after(endDate)) {
				timer.cancel();
				System.out
						.println("[MagentoSchedulerService] Timeout sevice stopped: "
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
	 * The new Workitem will be process with the ActivityID 800. The method also
	 * stores the property txtMagentoConfiguration with the id of the
	 * configuration entity
	 * 
	 * If the workitem still exits but the state did not match the $ProcessID of
	 * the workitem will be changed and th workitem will be processed withe
	 * ActivityID 801.
	 * 
	 * 
	 * The method implements a paging mechanism because magento returns maximum
	 * 100 order per request.
	 * 
	 * @param configuration
	 *            - the configuration entity for the magento shop system
	 * @throws PluginException
	 */
	@SuppressWarnings("unchecked")
	public void importOrders(ItemCollection configuration)
			throws MagentoException {
		int iProcessID = -1;
		String sMagentoStatus = null;
		String sShopID = configuration.getItemValueString("txtName");
		logger.info("[MagentoSchedulerSerivce] importOrders for magento shop id= "
				+ sShopID);

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

			// for some reasons it is not allowd to ask a state with space
			// characters!
			// so we check this now!
			if (sMagentoStatus == null || sMagentoStatus.isEmpty()
					|| sMagentoStatus.contains(" ")) {
				logger.warning("[MagentoSchedulerService] wrong order status mapping in '"
						+ mapping + "' - check configuration");
				continue;
			}

			try {
				logger.info("[MagentoSchedulerSerivce] read orders "
						+ " orderstatus=" + sMagentoStatus);

				List<ItemCollection> orders = magentoService.getRestClient(
						sShopID).getOrders(sMagentoStatus);

				logger.info("[MagentoSchedulerSerivce] " + orders.size()
						+ " orders found, start processing....");

				// process order list
				processOrderList(orders, orderModelVersion, iProcessID, sShopID);
			} catch (Exception e) {
				String errorMessage = "unable to read orders "
						+ " orderstatus=" + sMagentoStatus + " error message="
						+ e.getMessage();
				throw new MagentoException(IMPORT_ERROR, IMPORT_ERROR,
						errorMessage, e);
			}

		}

	}

	/**
	 * This method processes the orders read form magento. A new or changed
	 * workitem will be process by the activity ID 800.
	 * 
	 * The method also stores the property txtMagentoConfiguration with the id
	 * of the configuration entity
	 * 
	 * 
	 * @param orders
	 *            - list of orders
	 * @throws ModelException 
	 */
	private void processOrderList(List<ItemCollection> orders,
			String orderModelVersion, int iProcessID, String shopConfigID) throws ModelException {

		/*
		 * check if an activity 800 in the current model exits
		 */
		ItemCollection activityEntity = workflowService.getModelManager().getModel(orderModelVersion).getEvent(iProcessID,
						MagentoPlugin.ACTIVITY_MAGENTO_UPDATE);
		if (activityEntity == null) {
			logger.warning("[MagentoScheduler] - Activity " + iProcessID + "."
					+ MagentoPlugin.ACTIVITY_MAGENTO_UPDATE + " not defined!");
			return;
		}

		// verify orders....
		for (ItemCollection order : orders) {

			try {
				// store shopID
				if (!shopConfigID
						.equals(order
								.getItemValueString(MagentoPlugin.MAGENTO_CONFIGURATION_ID))) {
					order.replaceItemValue(
							MagentoPlugin.MAGENTO_CONFIGURATION_ID,
							shopConfigID);
				}

				boolean bUpdate = false;
				String sMagentoKey = magentoService.getOrderID(order);

				// check if workitem exits....
				ItemCollection workitem = magentoService
						.findWorkitemByOrder(order);

				if (workitem == null) {
					// create new order !
					logger.fine("[MagentoSchedulerService] create new workitem: '"
							+ sMagentoKey + "'");
					workitem = new ItemCollection();
					workitem.replaceItemValue("type", "workitem");
					workitem.replaceItemValue("txtName", sMagentoKey);
					workitem.replaceItemValue(WorkflowService.MODELVERSION,
							orderModelVersion);
					workitem.replaceItemValue("$ProcessID", new Integer(
							iProcessID));
					workitem.replaceItemValue("txtMagentoError", "");

					// store magento Shop id
					workitem.replaceItemValue(
							MagentoPlugin.MAGENTO_CONFIGURATION_ID,
							shopConfigID);

					// transfer order items
					magentoService.addMagentoEntity(workitem, order);
					bUpdate = true;
					workitemsImported++;

				} else {

					logger.fine("[MagentoSchedulerService] Workitem for order '"
							+ sMagentoKey
							+ "' already exists ("
							+ workitem
									.getItemValueString(WorkflowService.UNIQUEID)
							+ ")");
					// check if order details have changed
					if (!magentoService.isWorkitemEqualsToMagentoEntity(
							workitem, order)) {
						logger.fine("[MagentoSchedulerService] Workitem not equal! Update needed...");
						magentoService.addMagentoEntity(workitem, order);

						workitem.replaceItemValue("txtMagentoError", "");

						bUpdate = true;
						workitemsUpdated++;
					}

				}

				magentoOrdersTotal++;

				if (bUpdate) {
					// process activityId = 800
					workitem.replaceItemValue("$ActivityID", new Integer(
							MagentoPlugin.ACTIVITY_MAGENTO_UPDATE));
					ctx.getBusinessObject(MagentoSchedulerService.class)
							.processSingleWorkitem(workitem);

				}

			} catch (PluginException e) {
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

	/**
	 * Create an interval timer whose first expiration occurs at a given point
	 * in time and whose subsequent expirations occur after a specified
	 * interval.
	 **/
	Timer createTimerOnInterval(ItemCollection configItemCollection) {

		// Create an interval timer
		Date startDate = configItemCollection.getItemValueDate("datstart");
		Date endDate = configItemCollection.getItemValueDate("datstop");
		long interval = configItemCollection.getItemValueInteger("numInterval");
		// if endDate is in the past we do not start the timer!
		Calendar calNow = Calendar.getInstance();
		Calendar calEnd = Calendar.getInstance();

		if (endDate != null)
			calEnd.setTime(endDate);
		if (calNow.after(calEnd)) {
			logger.warning("[MagentoSchedulerService] "
					+ configItemCollection.getItemValueString("txtName")
					+ " stop-date is in the past");

			endDate = startDate;
		}
		
		XMLItemCollection xmlConfigItem = null;
		try {
			xmlConfigItem = XMLItemCollectionAdapter
					.putItemCollection(configItemCollection);
		} catch (Exception e) {
			logger.severe("Unable to serialize confitItemCollection into a XML object");
			e.printStackTrace();
			return null;
		}
		
		Timer timer = timerService.createTimer(startDate, interval,
				xmlConfigItem);

		return timer;

	}

	/**
	 * Create a calendar-based timer based on a input schedule expression. The
	 * expression will be parsed by this method.
	 * 
	 * Example: <code>
	 *   second=0
	 *   minute=0
	 *   hour=*
	 *   dayOfWeek=
	 *   dayOfMonth=25–Last,1–5
	 *   month=
	 *   year=*
	 * </code>
	 * 
	 * @param sConfiguation
	 * @return
	 * @throws ParseException
	 */
	Timer createTimerOnCalendar(ItemCollection configItemCollection)
			throws ParseException {

		TimerConfig timerConfig = new TimerConfig();
		
		XMLItemCollection xmlConfigItem = null;
		try {
			xmlConfigItem = XMLItemCollectionAdapter
					.putItemCollection(configItemCollection);
		} catch (Exception e) {
			logger.severe("Unable to serialize confitItemCollection into a XML object");
			e.printStackTrace();
			return null;
		}
		
		timerConfig.setInfo(xmlConfigItem);
		ScheduleExpression scheduerExpression = new ScheduleExpression();

		@SuppressWarnings("unchecked")
		List<String> calendarConfiguation = configItemCollection
				.getItemValue("txtConfiguration");
		// try to parse the configuration list....
		for (String confgEntry : calendarConfiguation) {

			if (confgEntry.startsWith("second=")) {
				scheduerExpression.second(confgEntry.substring(confgEntry
						.indexOf('=') + 1));
			}
			if (confgEntry.startsWith("minute=")) {
				scheduerExpression.minute(confgEntry.substring(confgEntry
						.indexOf('=') + 1));
			}
			if (confgEntry.startsWith("hour=")) {
				scheduerExpression.hour(confgEntry.substring(confgEntry
						.indexOf('=') + 1));
			}
			if (confgEntry.startsWith("dayOfWeek=")) {
				scheduerExpression.dayOfWeek(confgEntry.substring(confgEntry
						.indexOf('=') + 1));
			}
			if (confgEntry.startsWith("dayOfMonth=")) {
				scheduerExpression.dayOfMonth(confgEntry.substring(confgEntry
						.indexOf('=') + 1));
			}
			if (confgEntry.startsWith("month=")) {
				scheduerExpression.month(confgEntry.substring(confgEntry
						.indexOf('=') + 1));
			}
			if (confgEntry.startsWith("year=")) {
				scheduerExpression.year(confgEntry.substring(confgEntry
						.indexOf('=') + 1));
			}
			if (confgEntry.startsWith("timezone=")) {
				scheduerExpression.timezone(confgEntry.substring(confgEntry
						.indexOf('=') + 1));
			}

			/* Start date */
			if (confgEntry.startsWith("start=")) {
				SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
				Date convertedDate = dateFormat.parse(confgEntry
						.substring(confgEntry.indexOf('=') + 1));
				scheduerExpression.start(convertedDate);
			}

			/* End date */
			if (confgEntry.startsWith("end=")) {
				SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
				Date convertedDate = dateFormat.parse(confgEntry
						.substring(confgEntry.indexOf('=') + 1));
				scheduerExpression.end(convertedDate);
			}

		}

		
		Timer timer = timerService.createCalendarTimer(scheduerExpression,
				timerConfig);

		return timer;

	}

}
