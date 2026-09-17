/**
 * Portions Copyright 2015-2018 Mohawk College of Applied Arts and Technology
 * Portions Copyright (c) 2014-2020 Fyfe Software Inc.
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
package org.openmrs.module.santedb.mpiclient.configuration;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.marc.everest.formatters.FormatterUtil;
import org.openmrs.GlobalProperty;
import org.openmrs.api.context.Context;

/**
 * Health information exchange configuration
 * @author Justin
 *
 */
public class MpiClientConfiguration {

	// Lock object
	private static final Object s_lockObject = new Object();
	// Singleton
	private static MpiClientConfiguration s_instance;
	
	// Local identifier map
	private HashMap<String, String> m_localIdentifierMap;
	
	
	private final Log log = LogFactory.getLog(this.getClass());
	
	public static final String PROP_NAME_MESSAGE_FORMAT = "mpi-client.endpoint.format";
	
	public static final String PROP_NAME_PDQ_EP = "mpi-client.endpoint.pdq.addr";
	public static final String PROP_NAME_PDQ_EP_PORT = "mpi-client.endpoint.pdq.port";
	public static final String PROP_NAME_PIX_EP = "mpi-client.endpoint.pix.addr";
	public static final String PROP_NAME_PIX_EP_PORT = "mpi-client.endpoint.pix.port";

	public static final String PROP_NAME_ENT_ID = "mpi-client.pid.enterprise";
	public static final String PROP_NAME_LOCAL_ID = "mpi-client.pid.local";
	public static final String PROP_NAME_PREF_ID = "mpi-client.pid.nhid";
	public static final String PROP_NAME_AUTO_PIT = "mpi-client.pid.updateIdTypes";

	public static final String PROP_NAME_MSH_8 = "mpi-client.security.authtoken";
	public static final String PROP_NAME_IDP_ENDPOINT = "mpi-client.security.idp.addr";

	public static final String PROP_NAME_JKSTRUST_STORE = "mpi-client.security.trustStore";
	public static final String PROP_NAME_JKSTRUST_PASS = "mpi-client.security.trustStorePassword";
	public static final String PROP_NAME_JKSKEY_STORE = "mpi-client.security.keyStore";
	public static final String PROP_NAME_JKSKEY_PASS = "mpi-client.security.keyStorePassword";
	
	public static final String PROP_NAME_AR_ENDPOINT = "mpi-client.endpoint.ar.addr";
	public static final String PROP_NAME_AR_TRANSPORT = "mpi-client.endpoint.ar.transport";
	public static final String PROP_NAME_AR_PORT = "mpi-client.endpoint.ar.port";
	public static final String PROP_NAME_AR_LOCAL = "mpi-client.endpoint.ar.bind";
	
	public static final String PROP_NAME_SND_NAME = "mpi-client.msg.sendingApplication";
	public static final String PROP_NAME_SND_FAC = "mpi-client.msg.sendingFacility";
	public static final String PROP_NAME_RCV_NAME = "mpi-client.msg.remoteApplication";
	public static final String PROP_NAME_RCV_FAC = "mpi-client.msg.remoteFacility";

	public static final String PROP_NAME_EXTMAP = "mpi-client.ext.extendedAttributes";
	public static final String PROP_NAME_USE_OMRS_RELS = "mpi-client.ext.storeNK1AsRelationships";

	public static final String PROP_NAME_ID_EXPORT_TYPE = "mpi-client.pid.exportIdentitiferType";
	public static final String PROP_NAME_PAT_NAME_REWRITE = "mpi-client.pid.nameRewriteRegex";
	public static final String PROP_NAME_DEFAULT_COUNTRY = "mpi-client.pid.defaultCountry";
	
	public static final String PROP_NAME_USE_THREADS = "mpi-client.backgrounThreads";
	public static final String PROP_NAME_PREFER_CORR_AA = "mpi-client.pid.correlation";
	public static final String PROP_NAME_AUTO_PIX = "mpi-client.pid.autoXref";
	public static final String PROP_SEARCH_DATE_FUZZ = "mpi-client.search.dateFuzz";
	
	public static final String PROP_HTTP_PROXY = "mpi-client.http.proxyAddress";
	public static final String PROP_AUTH_TYPE = "mpi-client.security.authType";
	public static final String PROP_HL7_TLS = "mpi-client.security.tls";
	
    private Map<String, Object> m_cachedProperties = new HashMap<String, Object>();

	
	/**
     * Read a global property
     */
    private <T> T getOrCreateGlobalProperty(String propertyName, T defaultValue)
    {
    	// For debugging purposes caching of properties disabled
		//Object retVal = this.m_cachedProperties.get(propertyName);
		
		//if(retVal != null)
		//	return (T)retVal;
		//else 
		//{
    	
			String propertyValue = Context.getAdministrationService().getGlobalProperty(propertyName);
			this.log.info(String.format("Loaded MPI property: %s", propertyValue));
			if(propertyValue != null && !propertyValue.isEmpty())
			{
				T value = (T)FormatterUtil.fromWireFormat(propertyValue, defaultValue.getClass()); 
				synchronized (s_lockObject) {
					this.m_cachedProperties.put(propertyName, value);
                }
				return value;
			}
			else
			{
				Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(propertyName, defaultValue.toString()));
				synchronized (s_lockObject) {
					this.m_cachedProperties.put(propertyName, defaultValue);
                }
				return defaultValue;
			}
		//}
    }

	

	/**
	 * HIE configuration utility
	 */
	MpiClientConfiguration() {
		
	}
	
	/**
	 * Get the instance of the configuration utility
	 * @return
	 */
	public static MpiClientConfiguration getInstance()
	{
		if(s_instance == null)
			synchronized (s_lockObject) {
				if(s_instance == null)
					s_instance = new MpiClientConfiguration();
			}
		return s_instance;
	}
	
	/**
	 * Clears cached properties
	 */
	public void clearCache() {
		this.m_cachedProperties.clear();
	}
	
	/**
	 * Gets the message format
	 * @return
	 */
	public String getProxy() {
		return this.getOrCreateGlobalProperty(PROP_HTTP_PROXY, "");
	}
	
	/**
	 * Gets the message format
	 * @return
	 */
	public String getMessageFormat() {
		return this.getOrCreateGlobalProperty(PROP_NAME_MESSAGE_FORMAT, "hl7");
	}
	
	/**
	 * Gets whether background threads are to be used
	 * @return
	 */
	public Boolean getUseBackgroundThreads() {
		return this.getOrCreateGlobalProperty(PROP_NAME_USE_THREADS, false);
	}
	
	/**
	 * Gets whether background threads are to be used
	 * @return
	 */
	public String getPreferredCorrelationDomain() {
		return this.getOrCreateGlobalProperty(PROP_NAME_PREFER_CORR_AA, "");
	}
	
	/**
	 * Gets whether background threads are to be used
	 * @return
	 */
	public String getIdentityProviderUrl() {
		return this.getOrCreateGlobalProperty(PROP_NAME_IDP_ENDPOINT, "");
	}
	
	/**
     * Get whether messages should use TLS
     * @return 
     */
    public Boolean getUseTls() {
    	return this.getOrCreateGlobalProperty(PROP_HL7_TLS, false);
    }
    
	/**
	 * Gets whether background threads are to be used
	 * @return
	 */
	public String getAutomaticCrossReferenceDomains() {
		return this.getOrCreateGlobalProperty(PROP_NAME_AUTO_PIX, this.getNationalPatientIdRoot());
	}
	
	/**
	 * Get the enterprise patient identifier root
	 * @return
	 */
	public String getEnterprisePatientIdRoot() {
		return this.getOrCreateGlobalProperty(PROP_NAME_ENT_ID, "ENTID");
	}
	
	/**
	 * Get the default country
	 * @return
	 */
	public String getDefaultCountry() {
		return this.getOrCreateGlobalProperty(PROP_NAME_DEFAULT_COUNTRY, "");
	}
	
	/**
	 * Get the enterprise patient identifier root
	 * @return
	 */
	public String getLocalPatientIdRoot() {
		return this.getOrCreateGlobalProperty(PROP_NAME_LOCAL_ID, "LOCAL");
	}

	/**
	 * Get the enterprise patient identifier root
	 * @return
	 */
	public String getNationalPatientIdRoot() {
		return this.getOrCreateGlobalProperty(PROP_NAME_PREF_ID, "NAT_HEALTH_ID");
	}

	/**
	 * Get the enterprise patient identifier root
	 * @return
	 */
	public HashMap<String, String> getLocalPatientIdentifierTypeMap() {
		
		if(this.m_localIdentifierMap == null) {
			String exportType = this.getOrCreateGlobalProperty(PROP_NAME_ID_EXPORT_TYPE, "");
			this.m_localIdentifierMap = new HashMap<String, String>();
			if(exportType != null && !exportType.isEmpty())
				for(String st : exportType.split(","))
				{
					String[] dat = st.split("=");
					this.log.info(String.format("MPI Identifier Mapping: %s = %s", dat[0], dat[1]));
					this.m_localIdentifierMap.put(dat[0], dat[1]);
				}
			else 
				this.log.warn("No MPI identifier maps found");
		}
		return this.m_localIdentifierMap;
	}

    /**
     * Get the PDQ endpoint
     * @return
     */
    public String getPdqEndpoint() {
    	return this.getOrCreateGlobalProperty(PROP_NAME_PDQ_EP, "127.0.0.1");
    }
    
    /**
     * Get the authentication type port
     * @return
     */
    public String getAuthenticationMode() {
    	return this.getOrCreateGlobalProperty(PROP_AUTH_TYPE, "");
    }
    /**
     * Get the PDQ port
     * @return
     */
    public Integer getPdqPort() {
    	return this.getOrCreateGlobalProperty(PROP_NAME_PDQ_EP_PORT, 2100);
    }
    
    /**
     * Get the PDQ port
     * @return
     */
    public Integer getPdqDateFuzz() {
    	return this.getOrCreateGlobalProperty(PROP_SEARCH_DATE_FUZZ, 0);
    }
    
    /**
     * Get the PIX Endpoint
     * @return
     */
    public String getPixEndpoint() {
    	return this.getOrCreateGlobalProperty(PROP_NAME_PIX_EP, "127.0.0.1");
    }
    
    /**
     * Get the PIX POrt
     * @return
     */
    public Integer getPixPort() {
    	return this.getOrCreateGlobalProperty(PROP_NAME_PIX_EP_PORT, 2100);
    }
    
    /**
     * Get the XDS Registry endpoint
     * @return
     */
    public String getMsh8Security() {
    	return this.getOrCreateGlobalProperty(PROP_NAME_MSH_8, "");
    }
    
    /**
     * Get the audit repository endpoint
     * @return
     */
    public String getAuditRepositoryEndpoint() { return this.getOrCreateGlobalProperty(PROP_NAME_AR_ENDPOINT, "127.0.0.1"); }
    /**
     * Get the audit repository trasnport
     * @return
     */
    public String getAuditRepositoryTransport() { return this.getOrCreateGlobalProperty(PROP_NAME_AR_TRANSPORT, "audit-udp"); }
    /**
     * Get the audit repository port
     * @return
     */
    public int getAuditRepositoryPort() { return this.getOrCreateGlobalProperty(PROP_NAME_AR_PORT, 514); }
    /**
     * Get the key store file name
     * @return
     */
    public String getKeyStoreFile() { return this.getOrCreateGlobalProperty(PROP_NAME_JKSKEY_STORE, ""); }
    /**
     * Get the key store password
     * @return
     */
    public String getKeyStorePassword() { return this.getOrCreateGlobalProperty(PROP_NAME_JKSKEY_PASS, ""); }
    /**
     * Get the trust store file name
     * @return
     */
    public String getTrustStoreFile() { return this.getOrCreateGlobalProperty(PROP_NAME_JKSTRUST_STORE, ""); }
    /**
     * Get the trust store password
     * @return
     */
    public String getTrustStorePassword() { return this.getOrCreateGlobalProperty(PROP_NAME_JKSTRUST_PASS, ""); }
    
    /**
     * Gets the device name
     * @return The device name
     */
    public String getLocalApplication() { return this.getOrCreateGlobalProperty(PROP_NAME_SND_NAME, "OpenMRS"); }
    
    /**
     * Gets the facility name
     * @return The facility name
     */
    public String getLocalFacility() { return this.getOrCreateGlobalProperty(PROP_NAME_SND_FAC, "FACILITY_ID"); }
    
    /**
     * Gets the device name
     * @return The device name
     */
    public String getRemoteApplication() { return this.getOrCreateGlobalProperty(PROP_NAME_RCV_NAME, "MPI"); }
    
    /**
     * Gets the facility name
     * @return The facility name
     */
    public String getRemoteFacility() { return this.getOrCreateGlobalProperty(PROP_NAME_RCV_FAC, "NATION_ID"); }
    
    /**
     * Gets the name of the patient attribute which is the mother's name (to go in the PID segment)
     */
    public HashMap<String, String> getExtensionMap() { 
    	String propertyData = this.getOrCreateGlobalProperty(PROP_NAME_EXTMAP, "");
    	HashMap<String, String> retVal = new HashMap<String, String>();
    	if(!propertyData.isEmpty()) {
    		for(String kv : propertyData.split(","))
    		{
    			String[] key = kv.split(":");
    			retVal.put(key[0], key[1]);
    		}
    	}
    	return retVal;
	}    
    /**
     * Gets the fathers name patient attribute to be sent in a NK1 segment
     */
    public boolean getUseOpenMRSRelationships() { return this.getOrCreateGlobalProperty(PROP_NAME_USE_OMRS_RELS, false); }
    
    /**
     * Gets the auto update 
     * @return
     */
    public boolean getAutoUpdateLocalPatientIdentifierTypes() { return this.getOrCreateGlobalProperty(PROP_NAME_AUTO_PIT, false); }
    
    public String getNameRewriteRule() { return this.getOrCreateGlobalProperty(PROP_NAME_PAT_NAME_REWRITE, ""); }

    public String getAuditRepositoryBindAddress() { return this.getOrCreateGlobalProperty(PROP_NAME_AR_LOCAL, ""); }
}