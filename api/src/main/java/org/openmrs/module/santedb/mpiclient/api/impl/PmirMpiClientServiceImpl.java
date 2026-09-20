package org.openmrs.module.santedb.mpiclient.api.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dcm4che3.audit.AuditMessage;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.Bundle.BundleLinkComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Bundle.HTTPVerbEnumFactory;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.MessageHeader.MessageDestinationComponent;
import org.hl7.fhir.r4.model.MessageHeader.MessageSourceComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.UrlType;
import org.openmrs.Patient;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.util.AuditUtil;
import org.springframework.web.util.UriTemplate;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;

/**
 * Specialization of the FHIR client which implements PMIR
 */
public class PmirMpiClientServiceImpl extends FhirMpiClientServiceImpl {

	// Log
	private static Log log = LogFactory.getLog(PmirMpiClientServiceImpl.class);
	
	/** 
	 * Get the full URL
	 */
	private String getFullUrl(Resource fhirResource) {
		return String.format("urn:uuid:%s", fhirResource.getId());
	}
	
	/** 
	 * Create the PMIR bundle
	 * @return
	 * @throws MpiClientException 
	 */
	private Bundle createPmirBundle(HTTPVerb verb, Patient patient) throws MpiClientException {
		
		try {
			Bundle retVal = new Bundle();
			
			Bundle focalBundle = new Bundle();
			focalBundle.setId(UUID.randomUUID().toString());
			focalBundle.setType(BundleType.HISTORY);
			
			// Construct the standard PMIR header
			retVal.setType(BundleType.MESSAGE);
			BundleEntryComponent messageHeaderEntry = new BundleEntryComponent();
			MessageHeader messageHeader = new MessageHeader();
			messageHeader.setId(UUID.randomUUID().toString());
			messageHeaderEntry.setFullUrl(this.getFullUrl(messageHeader));
			messageHeader.setEvent(new UriType("urn:ihe:iti:pmir:2019:patient-feed"));
			messageHeader.setSource(new MessageSourceComponent(new UrlType(String.format("urn:santedb:openmrs:mpi-client:%s", this.m_configuration.getLocalApplication()))));
			messageHeader.setFocus(new ArrayList<Reference>());
			messageHeader.getFocus().add(new Reference(this.getFullUrl(focalBundle)));
			messageHeader.setDestination(new ArrayList<MessageHeader.MessageDestinationComponent>());
			messageHeader.getDestination().add(new MessageDestinationComponent(new UrlType(this.m_configuration.getPixEndpoint())));
			messageHeaderEntry.setResource(messageHeader);
			retVal.addEntry(messageHeaderEntry);
			
			BundleEntryComponent focalEntry = new BundleEntryComponent();
			focalEntry.setResource(focalBundle);
			focalEntry.setFullUrl(this.getFullUrl(focalBundle));
			focalEntry.setResource(focalBundle);
			retVal.addEntry(focalEntry);
	
			BundleEntryComponent patientEntry = new BundleEntryComponent();
			org.hl7.fhir.r4.model.Patient focalPatient = this.m_messageUtil.createFhirPatient(patient, false);
			patientEntry.setResource(focalPatient);
			patientEntry.addLink(new BundleLinkComponent(new StringType("about"), new UrlType(this.getFullUrl(focalPatient))));
			patientEntry.setFullUrl(this.getFullUrl(focalPatient));
			patientEntry.setRequest(new BundleEntryRequestComponent());
			patientEntry.getRequest().setMethod(verb);
			patientEntry.getRequest().setUrl(this.getFullUrl(focalPatient).replace("urn:uuid:", "Patient/"));
			focalBundle.addEntry(patientEntry);
			return retVal;
		}
		catch (Exception e) {
			e.printStackTrace();
			log.error(e);
			throw new MpiClientException(e);
		} finally {
		}
	}
	
	/**
	 * Export a patient using the PMIR profile
	 */
	@Override
	public void exportPatient(Patient patient) throws MpiClientException {
		
		try {
			
			Bundle admitMessage = this.createPmirBundle(HTTPVerb.PUT, patient);
			
			IGenericClient client = this.getClient(false);
			MethodOutcome result = client.create().resource(admitMessage).execute();
			if (!result.getCreated())
				throw new MpiClientException(
						String.format("Error from MPI :> %s", result.getResource().getClass().getName()));
			
		} catch (MpiClientException e) {
			log.error("Error in FHIR PMIR message", e);
			e.printStackTrace();
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e);
			throw new MpiClientException(e);
		} finally {
		}

	}

	@Override
	public void updatePatient(Patient patient) throws MpiClientException {
try {
			
			Bundle admitMessage = this.createPmirBundle(HTTPVerb.PUT, patient);
			
			IGenericClient client = this.getClient(false);
			MethodOutcome result = client.create().resource(admitMessage).execute();
			if (!result.getCreated())
				throw new MpiClientException(
						String.format("Error from MPI :> %s", result.getResource().getClass().getName()));
			
		} catch (MpiClientException e) {
			log.error("Error in FHIR PMIR message", e);
			e.printStackTrace();
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e);
			throw new MpiClientException(e);
		} finally {
		}
	}

	

}
