package org.imixs.workflow.magento.soap;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.naming.NamingException;

import junit.framework.Assert;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.PropertyService;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.magento.MagentoClient;
import org.imixs.workflow.magento.MagentoClientFactory;
import org.imixs.workflow.magento.MagentoException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Dieser test testet die Magento Schnittstelle
 * 
 * 
 * Das MagentoPlugin wird hier über Mockito gemockt. Dazu ist es notwendig die
 * MockitioInitialContextFactory über folgendes VM Argument einzubinden!
 * 
 * 
 * <code>
 * -Djava.naming.factory.initial=org.imixs.workflow.magento.test.MockitioInitialContextFactory
 * </code>
 * 
 * 
 * 
 * @see: https://github.com/fernandezpablo85/scribe-java
 * 
 * 
 * 
 *       Set log level
 * 
 *       http://stackoverflow.com/questions/14235726/junit4-unit-tests-running-
 *       inside-eclipse-using-java-util-logging-cannot-see-l
 * 
 * 
 * 
 */
public class TestMagentoSoapClient {
	MagentoClient magentoClient = null;
	PropertyService propertyService = null;
	Properties properties = null;

	Map<String, ItemCollection> database = new HashMap<String, ItemCollection>();

	@Before
	public void setup() throws PluginException, IOException, NamingException {

		if (magentoClient!=null) {
			return;
		}
	
		magentoClient =  MagentoClientFactory
				.createClient("org.imixs.workflow.magento.soap.MagentoSOAPClient");

		ItemCollection config = new ItemCollection();

		config.replaceItemValue("txtMagentoSOAPAccessKey",
				properties.getProperty("magento.soap.access-key"));

		config.replaceItemValue("txtMagentoAccessSecret",
				properties.getProperty("magento.soap.access-secret"));

		magentoClient.connect(config);

	}

	/**
	 * This Test checks the getProducts method...
	 * 
	 */
	@Ignore
	@Test
	public void testGetProducts() {

		List<ItemCollection> result = null;
		try {
			result = magentoClient.getProducts();
		} catch (PluginException e) {

			e.printStackTrace();
			Assert.fail();
		}

		Assert.assertNotNull(result);
		Assert.assertTrue(result.size() > 0);
		ItemCollection entity = result.get(0);

		Assert.assertTrue(entity.hasItem("sku"));
		Assert.assertTrue(entity.hasItem("product_id"));
		Assert.assertTrue(entity.hasItem("name"));
	}



	/**
	 * This Test checks the Magento Connection...
	 * http://localhost/magento/rest/api/products/1
	 * 
	 */
	@Ignore
	@Test
	public void testGetProductBySKU() {

		ItemCollection result = null;

		try {
			result = magentoClient.getProductBySKU("100");
		} catch (MagentoException e) {
			e.printStackTrace();
			Assert.fail();
		}

		Assert.assertTrue(result.hasItem("product_id"));
		Assert.assertFalse(result.getItemValueString("product_id").isEmpty());
		Assert.assertEquals("100", result.getItemValueString("sku"));
		Assert.assertEquals("simple", result.getItemValueString("type_id"));

		Assert.assertEquals("Imixs Business Servicevertrag",
				result.getItemValueString("short_description"));

	}

	
	/**
	 * This Test adds a comment
	 * 
	 */
	@Ignore
	@Test
	public void testAddComment() {

		
		try {
			magentoClient.addOrderComment("100000012","pending","junit test",false);
			
		} catch (MagentoException e) {
			e.printStackTrace();
			Assert.fail();
		}


	}

	
	
	/**
	 * This Test checks the Magento Connection...
	 * 
	 */
	@Ignore
	@Test
	public void testGetPendingOrders() {

		List<ItemCollection> result = null;
		try {
			result = magentoClient.getOrders("pending");
		} catch (PluginException e) {

			e.printStackTrace();
			Assert.fail();
		}

		Assert.assertNotNull(result);
		Assert.assertTrue(result.size() > 4);
		ItemCollection entity = result.get(0);
		Assert.assertTrue(entity.hasItem("order_id"));
		Assert.assertEquals("pending", entity.getItemValueString("status"));

	}

}
