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
package org.openmrs.module.santedb.mpiclient.api.impl;

import java.util.Date;
import java.util.List;

import org.dcm4che3.net.audit.AuditLogger;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.santedb.mpiclient.api.MpiClientService;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.dao.MpiClientDao;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;

/**
 * Implementation of the health information exchange service
 * @author Justin
 *
 */
public class MpiClientServiceImpl extends BaseOpenmrsService
		implements MpiClientService {

	private FhirMpiClientServiceImpl m_fhirService;
	private PmirMpiClientServiceImpl m_pmirService;
	
	private HL7MpiClientServiceImpl m_hl7Service;
	// Get health information exchange information
	private MpiClientConfiguration m_configuration = MpiClientConfiguration.getInstance();

	// DAO
		private MpiClientDao dao;
		
	/**
	 * @param dao the dao to set
	 */
	public void setDao(MpiClientDao dao) {
		this.dao = dao;
		this.m_hl7Service.setDao(dao);
	}

	/**
	 * @summary Creates a new instance of the MPI Client Service Implementation
	 */
	public MpiClientServiceImpl() {
		this.m_fhirService = new FhirMpiClientServiceImpl(); // TODO: FHIR implementation
		this.m_hl7Service = new HL7MpiClientServiceImpl();
		this.m_pmirService = new PmirMpiClientServiceImpl();
	}

	/**
	 * Search patient from wrapped
	 */
	@Override
	public List<MpiPatient> searchPatient(String familyName, String givenName, Date dateOfBirth, boolean fuzzyDate,
			String gender, String stateOrRegion, String cityOrTownship, PatientIdentifier patientIdentifier,
			PatientIdentifier mothersIdentifier, String nextOfKinName, String birthPlace) throws MpiClientException {
		if("pmir".equals(MpiClientConfiguration.getInstance().getMessageFormat()))
			return this.m_pmirService.searchPatient(familyName, givenName, dateOfBirth, fuzzyDate, gender, stateOrRegion, cityOrTownship, patientIdentifier, mothersIdentifier, nextOfKinName, birthPlace);
		else if("fhir".equals(MpiClientConfiguration.getInstance().getMessageFormat()))
			return this.m_fhirService.searchPatient(familyName, givenName, dateOfBirth, fuzzyDate, gender, stateOrRegion, cityOrTownship, patientIdentifier, mothersIdentifier, nextOfKinName, birthPlace);
		else 
			return this.m_hl7Service.searchPatient(familyName, givenName, dateOfBirth, fuzzyDate, gender, stateOrRegion, cityOrTownship, patientIdentifier, mothersIdentifier, nextOfKinName, birthPlace);
			
	}

	/**
	 * Get patient using specified identifier and AA
	 */
	@Override
	public MpiPatient getPatient(String identifier, String assigningAuthority) throws MpiClientException {
		// TODO Auto-generated method stub
		if("pmir".equals(MpiClientConfiguration.getInstance().getMessageFormat()))
			return this.m_pmirService.getPatient(identifier, assigningAuthority);
		else if("fhir".equals(MpiClientConfiguration.getInstance().getMessageFormat()))
			return this.m_fhirService.getPatient(identifier, assigningAuthority);
		else 
			return this.m_hl7Service.getPatient(identifier, assigningAuthority);
	}

	/**
	 * Resolve patient identifier 
	 */
	@Override
	public PatientIdentifier resolvePatientIdentifier(Patient patient, String toAssigningAuthority)
			throws MpiClientException {
		// TODO Auto-generated method stub
		if("pmir".equals(MpiClientConfiguration.getInstance().getMessageFormat()))
			return this.m_pmirService.resolvePatientIdentifier(patient, toAssigningAuthority);
		else if("fhir".equals(MpiClientConfiguration.getInstance().getMessageFormat()))
			return this.m_fhirService.resolvePatientIdentifier(patient, toAssigningAuthority);
		else 
			return this.m_hl7Service.resolvePatientIdentifier(patient, toAssigningAuthority);
	}

	/**
	 * Synchronize patient with enterprise identifier
	 */
	@Override
	public void synchronizePatientEnterpriseId(Patient patient) throws MpiClientException {
		// Resolve patient identifier
		PatientIdentifier pid = this.resolvePatientIdentifier(patient, MpiClientConfiguration.getInstance().getEnterprisePatientIdRoot());
		if(pid != null)
		{
			PatientIdentifier existingPid = patient.getPatientIdentifier(pid.getIdentifierType());
			if(existingPid != null && !existingPid.getIdentifier().equals(pid.getIdentifier()))
			{
					existingPid.setIdentifier(pid.getIdentifier());
					Context.getPatientService().savePatientIdentifier(existingPid);	
			}
			else if(existingPid == null)
			{
				pid.setPatient(patient);
				Context.getPatientService().savePatientIdentifier(pid);
			}
			else
				return;
		}
		else
			throw new MpiClientException("Patient has been removed from the HIE");
	}

	/**
	 * Import patient with specified patient data
	 */
	@Override
	public Patient importPatient(MpiPatient patient) throws MpiClientException {
		// TODO Auto-generated method stub
		if("pmir".equals(MpiClientConfiguration.getInstance().getMessageFormat()))
			return this.m_pmirService.importPatient(patient);
		else if("fhir".equals(MpiClientConfiguration.getInstance().getMessageFormat()))
			return this.m_fhirService.importPatient(patient);
		else 
			return this.m_hl7Service.importPatient(patient);
	}

	/**
	 * Match an external patient with internal patient
	 * @see org.openmrs.module.santedb.mpiclient.api.MpiClientService#matchWithExistingPatient(org.openmrs.Patient)
	 */
	@Override
	public Patient matchWithExistingPatient(Patient remotePatient) {
		Patient candidate = null;
		
		if(remotePatient.getUuid() != null) {
			candidate = Context.getPatientService().getPatientByUuid(remotePatient.getUuid());
		}
		
		// Does this patient have an identifier from our assigning authority?
		if(candidate == null) {
			for(PatientIdentifier pid : remotePatient.getIdentifiers()) {
				if(pid.getIdentifierType() == null) continue;
				String domain = this.m_configuration.getLocalPatientIdentifierTypeMap().get(pid.getIdentifierType().getName());
				if(this.m_configuration.getLocalPatientIdRoot().equals(domain))
					try
					{
						candidate = Context.getPatientService().getPatient(Integer.parseInt(pid.getIdentifier()));
					}
					catch(Exception e)
					{
						
					}
			}
		}
		// This patient may be an existing patient, so we just don't want to add it!
		if(candidate == null)
			for(PatientIdentifier pid : remotePatient.getIdentifiers())
			{
				candidate = this.dao.getPatientByIdentifier(pid.getIdentifier(), pid.getIdentifierType());
				if(candidate != null)
					break;
			}
		
		return candidate;
    }

	
	/**
	 * Export patient using preferred messaging format
	 */
	@Override
	public void exportPatient(Patient patient) throws MpiClientException {
		// TODO Auto-generated method stub
		if("pmir".equals(MpiClientConfiguration.getInstance().getMessageFormat()))
			this.m_pmirService.exportPatient(patient);
		else if("fhir".equals(MpiClientConfiguration.getInstance().getMessageFormat()))
			this.m_fhirService.exportPatient(patient);
		else 
			this.m_hl7Service.exportPatient(patient);
	}

	/**
	 * Update patient in MPI
	 */
	@Override
	public void updatePatient(Patient patient) throws MpiClientException {
		// TODO Auto-generated method stub
		if("pmir".equals(MpiClientConfiguration.getInstance().getMessageFormat()))
			this.m_pmirService.updatePatient(patient);
		else if(MpiClientConfiguration.getInstance().getMessageFormat().equals("fhir"))
			this.m_fhirService.updatePatient(patient);
		else 
			this.m_hl7Service.updatePatient(patient);
	}

	/**
	 * GEt audit logger
	 */
	@Override
	public AuditLogger getAuditLogger() {
		// TODO Auto-generated method stub
		if("pmir".equals(MpiClientConfiguration.getInstance().getMessageFormat()))
			return this.m_fhirService.getAuditLogger();
		else if("fhir".equals(MpiClientConfiguration.getInstance().getMessageFormat()))
			return this.m_fhirService.getAuditLogger();
		else 
			return this.m_hl7Service.getAuditLogger();
	}
	
}
