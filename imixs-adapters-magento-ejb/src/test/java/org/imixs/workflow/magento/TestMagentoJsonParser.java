package org.imixs.workflow.magento;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.stream.JsonParser;

import junit.framework.Assert;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.magento.rest.MagentoJsonParser;
import org.junit.Test;

/**
 * This test class tests the MagenotJsonParser methods.
 * 
 * The class provides simple test cases and also test cases using external json
 * resources located in /src/test/resources
 * 
 * 
 */
public class TestMagentoJsonParser {

	final static String STOCK_ITMES = "[{\"item_id\":\"1\",\"product_id\":\"1\",\"stock_id\":\"1\",\"qty\":\"99.0000\",\"low_stock_date\":null},{\"item_id\":\"2\",\"product_id\":\"2\",\"stock_id\":\"1\",\"qty\":\"100.0000\",\"low_stock_date\":null}]";
	final static String ERROR_MESSAGE = "{\"messages\":{\"error\":[{\"code\":401,\"message\":\"oauth_problem=token_rejected\"}]}}";
	final static String ORDER_LIST = "{\"1\":{\"entity_id\":\"1\",\"status\":\"pending\",\"coupon_code\":null,\"shipping_description\":\"Flat Rate - Fixed\",\"customer_id\":null,\"base_discount_amount\":\"0.0000\",\"base_grand_total\":\"205.0000\",\"base_shipping_amount\":\"5.0000\",\"base_shipping_tax_amount\":\"0.0000\",\"base_subtotal\":\"200.0000\",\"base_tax_amount\":\"0.0000\",\"base_total_paid\":null,\"base_total_refunded\":null,\"discount_amount\":\"0.0000\",\"grand_total\":\"205.0000\",\"shipping_amount\":\"5.0000\",\"shipping_tax_amount\":\"0.0000\",\"store_to_order_rate\":\"1.0000\",\"subtotal\":\"200.0000\",\"tax_amount\":\"0.0000\",\"total_paid\":null,\"total_refunded\":null,\"base_shipping_discount_amount\":\"0.0000\",\"base_subtotal_incl_tax\":\"200.0000\",\"base_total_due\":null,\"shipping_discount_amount\":\"0.0000\",\"subtotal_incl_tax\":\"200.0000\",\"total_due\":null,\"increment_id\":\"100000001\",\"base_currency_code\":\"EUR\",\"discount_description\":null,\"remote_ip\":\"::1\",\"store_currency_code\":\"EUR\",\"store_name\":\"Main Website\nMain Website Store\nDefault Store View\",\"created_at\":\"2014-07-23 19:56:32\",\"shipping_incl_tax\":\"5.0000\",\"payment_method\":\"checkmo\",\"gift_message_from\":null,\"gift_message_to\":null,\"gift_message_body\":null,\"tax_name\":null,\"tax_rate\":null,\"addresses\":[{\"region\":\"Bayern\",\"postcode\":\"34535\",\"lastname\":\"Soika\",\"street\":\"Ag.\",\"city\":\"asdf\",\"email\":\"ralph.soika@imixs.com\",\"telephone\":\"23423\",\"country_id\":\"DE\",\"firstname\":\"Ralph\",\"address_type\":\"billing\",\"prefix\":null,\"middlename\":null,\"suffix\":null,\"company\":\"Imixs\"},{\"region\":\"Bayern\",\"postcode\":\"34535\",\"lastname\":\"Soika\",\"street\":\"Ag.\",\"city\":\"asdf\",\"email\":\"ralph.soika@imixs.com\",\"telephone\":\"23423\",\"country_id\":\"DE\",\"firstname\":\"Ralph\",\"address_type\":\"shipping\",\"prefix\":null,\"middlename\":null,\"suffix\":null,\"company\":\"Imixs\"}],\"order_items\":[{\"item_id\":\"1\",\"parent_item_id\":null,\"sku\":\"2\",\"name\":\"Enterprise Servicevertrag\",\"qty_canceled\":\"0.0000\",\"qty_invoiced\":\"0.0000\",\"qty_ordered\":\"1.0000\",\"qty_refunded\":\"0.0000\",\"qty_shipped\":\"0.0000\",\"price\":\"200.0000\",\"base_price\":\"200.0000\",\"original_price\":\"200.0000\",\"base_original_price\":\"200.0000\",\"tax_percent\":\"0.0000\",\"tax_amount\":\"0.0000\",\"base_tax_amount\":\"0.0000\",\"discount_amount\":\"0.0000\",\"base_discount_amount\":\"0.0000\",\"row_total\":\"200.0000\",\"base_row_total\":\"200.0000\",\"price_incl_tax\":\"200.0000\",\"base_price_incl_tax\":\"200.0000\",\"row_total_incl_tax\":\"200.0000\",\"base_row_total_incl_tax\":\"200.0000\"}],\"order_comments\":[{\"is_customer_notified\":\"1\",\"is_visible_on_front\":\"0\",\"comment\":null,\"status\":\"pending\",\"created_at\":\"2014-07-23 19:56:32\"}]}}";

	private final static Logger logger = Logger
			.getLogger(TestMagentoJsonParser.class.getName());

	/**
	 * This method test the parseError method.
	 * 
	 */
	@Test
	public void testJsonError() {

		logger.info("parsing a error message.....");
		// error message
		PluginException result = MagentoJsonParser.parseError(ERROR_MESSAGE);

		Assert.assertNotNull(result);
		Assert.assertEquals("401", result.getErrorCode());
		Assert.assertEquals("oauth_problem=token_rejected", result.getMessage());

		// test empty string
		result = MagentoJsonParser.parseError("");
		Assert.assertNull(result);

		// test non error string
		result = MagentoJsonParser.parseError(STOCK_ITMES);
		Assert.assertNull(result);

		logger.info("parsing a error message.....OK");

	}

	@Test
	public void testSimpleProductList() {

		// error message
		List<ItemCollection> result = null;
		try {
			result = MagentoJsonParser.parseObjectList(STOCK_ITMES);
		} catch (PluginException e) {
			e.printStackTrace();
			Assert.fail();
		}

		Assert.assertNotNull(result);
		Assert.assertEquals(2, result.size());

		ItemCollection entity = result.get(0);
		Assert.assertEquals("1", entity.getItemValueString("item_id"));
		Assert.assertEquals("1", entity.getItemValueString("product_id"));
		Assert.assertEquals("1", entity.getItemValueString("stock_id"));
		Assert.assertEquals(99.000, entity.getItemValueDouble("qty"));

		entity = result.get(1);
		Assert.assertEquals("2", entity.getItemValueString("item_id"));
		Assert.assertEquals("2", entity.getItemValueString("product_id"));
		Assert.assertEquals("1", entity.getItemValueString("stock_id"));
		Assert.assertEquals(100.000, entity.getItemValueDouble("qty"));

		// test unexpected error message
		try {
			result = MagentoJsonParser.parseObjectList(ERROR_MESSAGE);
			Assert.fail();
		} catch (PluginException e) {
			Assert.assertEquals("401", e.getErrorCode());
			Assert.assertEquals("oauth_problem=token_rejected", e.getMessage());
		}
	}

	/**
	 * This test imports the stockitems.json file for parsing.
	 * 
	 */
	@Test
	public void testStockitems() {

		// read stockitems.json
		String sJson=readFile("/stockitems.json");
		
		// error message
		List<ItemCollection> result = null;
		try {
			result = MagentoJsonParser.parseObjectList(sJson);
		} catch (PluginException e) {
			e.printStackTrace();
			Assert.fail();
		}

		Assert.assertNotNull(result);
		Assert.assertEquals(2, result.size());

		ItemCollection entity = result.get(0);
		Assert.assertEquals("1", entity.getItemValueString("item_id"));
		Assert.assertEquals("1", entity.getItemValueString("product_id"));
		Assert.assertEquals("1", entity.getItemValueString("stock_id"));
		Assert.assertEquals(68.0000, entity.getItemValueDouble("qty"));

		entity = result.get(1);
		Assert.assertEquals("2", entity.getItemValueString("item_id"));
		Assert.assertEquals("2", entity.getItemValueString("product_id"));
		Assert.assertEquals("1", entity.getItemValueString("stock_id"));
		Assert.assertEquals(1.0000, entity.getItemValueDouble("qty"));

		
	}

	
	/**
	 * This test imports the products.json file for parsing.
	 * 
	 */
	@Test
	public void testProducts() {

		// read stockitems.json
		String sJson=readFile("/products.json");
		
		// error message
		List<ItemCollection> result = null;
		try {
			result = MagentoJsonParser.parseObjectList(sJson);
		} catch (PluginException e) {
			e.printStackTrace();
			Assert.fail();
		}

		Assert.assertNotNull(result);
		Assert.assertEquals(2, result.size());

		ItemCollection entity = result.get(0);
		Assert.assertEquals("1", entity.getItemValueString("entity_id"));
		Assert.assertEquals("simple", entity.getItemValueString("type_id"));
		Assert.assertEquals(100.0000, entity.getItemValueDouble("price"));

		entity = result.get(1);
		Assert.assertEquals("2", entity.getItemValueString("entity_id"));
		Assert.assertEquals("simple", entity.getItemValueString("type_id"));
		Assert.assertEquals(200.0000, entity.getItemValueDouble("price"));

		
	}

	
	
	
	
	
	
	
	/**
	 * This test imports the order.json file for parsing.
	 * 
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testOrder() {

		// read stockitems.json
		String sJson=readFile("/order.json");
		
		// error message
		List<ItemCollection> result = null;
		try {
			result = MagentoJsonParser.parseObjectList(sJson);
		} catch (PluginException e) {
			e.printStackTrace();
			Assert.fail();
		}

		Assert.assertNotNull(result);
		Assert.assertEquals(3, result.size());

		ItemCollection entity = result.get(0); 
		Assert.assertEquals("1", entity.getItemValueString("entity_id"));
		Assert.assertEquals("pending", entity.getItemValueString("status"));
		Assert.assertEquals(100.0000, entity.getItemValueDouble("base_subtotal"));

		// test address
		List<Map<String,?>> addresses=entity.getItemValue("addresses");
		Assert.assertNotNull(addresses);
		Assert.assertEquals(2, addresses.size());
		// get embedded map...
		Map<String,List<Object>> addressMap = (Map<String,List<Object>>) addresses.get(0);
		ItemCollection address= new ItemCollection(addressMap);	
		
		Assert.assertEquals("Alabama", address.getItemValueString("region"));
		Assert.assertEquals("345", address.getItemValueString("postcode"));
		addressMap = (Map<String,List<Object>>) addresses.get(1);
		address= new ItemCollection(addressMap);	
		Assert.assertEquals("Alabama", address.getItemValueString("region"));
		Assert.assertEquals("345", address.getItemValueString("postcode"));
		
		
		
		// test orderitems
		List<Map<String,List<Object>>> orderitems=entity.getItemValue("order_items");
		Assert.assertNotNull(orderitems);
		Assert.assertEquals(1, orderitems.size());
		
		Map<String,List<Object>> orderItemMap = (Map<String,List<Object>>) orderitems.get(0);
		ItemCollection orderItem =new ItemCollection(orderItemMap);
		
		
		Assert.assertEquals("1", orderItem.getItemValueString("item_id"));
		Assert.assertEquals("Business Servicevertrag", orderItem.getItemValueString("name"));
		
	}

	
	
	
	
	
	
	
	

	/**
	 * This test imports the product_1.json file for parsing.
	 * 
	 */
	@Test
	public void testProduct_1() {

		// read stockitems.json
		String sJson=readFile("/product_1.json");
		
		// error message
		List<ItemCollection> result = null;
		try {
			result = MagentoJsonParser.parseObjectList(sJson);
		} catch (PluginException e) {
			e.printStackTrace();
			Assert.fail();
		}

		Assert.assertNotNull(result);
		Assert.assertEquals(1, result.size());

		ItemCollection entity = result.get(0); 
		Assert.assertEquals("1", entity.getItemValueString("entity_id"));
		
	}

	

	/**
	 * This test simply test the json parser class
	 * 
	 */
	@Test
	public void testJsonParser() {
	
		String json="{\"2\":{\"entity_id\":\"2\",\"attribute_set_id\":\"4\",\"type_id\":\"simple\",\"sku\":\"1\",\"status\":\"1\",\"visibility\":\"4\",\"enable_googlecheckout\":\"1\",\"tax_class_id\":\"0\",\"manufacturer\":\"3\",\"weight\":\"1.0000\",\"price\":\"200.0000\",\"special_price\":null,\"msrp\":null,\"name\":\"Enterprise Servicevertrag\",\"url_key\":\"enterprise-servicevertrag\",\"country_of_manufacture\":null,\"msrp_enabled\":\"2\",\"msrp_display_actual_price_type\":\"4\",\"meta_title\":null,\"meta_description\":null,\"custom_design\":null,\"page_layout\":null,\"options_container\":\"container2\",\"gift_message_available\":null,\"news_from_date\":null,\"news_to_date\":null,\"special_from_date\":null,\"special_to_date\":null,\"custom_design_from\":null,\"custom_design_to\":null,\"description\":\"Imixs Enterprise Servicevertrag\",\"short_description\":\"Imixs Enterprise Servicevertrag\",\"meta_keyword\":null,\"custom_layout_update\":null}}";
		JsonParser parser = Json.createParser(new StringReader(json));

		Assert.assertNotNull(parser);
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	/**
	 * converts inputstream into string
	 */
	private String readFile(String filepath) {
		InputStream is = getClass().getResourceAsStream(filepath);
		int k;
		StringBuffer sb = new StringBuffer();
		try {
			while ((k = is.read()) != -1) {
				sb.append((char) k);
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return sb.toString();
	}
}
