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
package org.openmrs.module.santedb.mpiclient.util;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Address.AddressUse;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.HumanName.NameUse;
import org.hl7.fhir.r4.model.Identifier;
import org.marc.everest.datatypes.TS;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.Relationship;
import org.openmrs.RelationshipType;
import org.openmrs.api.context.Context;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.app.Connection;
import ca.uhn.hl7v2.app.ConnectionHub;
import ca.uhn.hl7v2.app.Initiator;
import ca.uhn.hl7v2.llp.LLPException;
import ca.uhn.hl7v2.llp.MinLowerLayerProtocol;
import ca.uhn.hl7v2.model.AbstractPrimitive;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Group;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.v25.datatype.CX;
import ca.uhn.hl7v2.model.v25.datatype.XAD;
import ca.uhn.hl7v2.model.v25.datatype.XPN;
import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.model.v25.message.QBP_Q21;
import ca.uhn.hl7v2.model.v25.segment.MSH;
import ca.uhn.hl7v2.model.v25.segment.NK1;
import ca.uhn.hl7v2.model.v25.segment.PID;
import ca.uhn.hl7v2.model.v25.segment.SFT;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;

/**
 * Message utilities used by the API
 * 
 * @author Justin
 *
 */
public final class MessageUtil {

	private final Log log = LogFactory.getLog(this.getClass());

	// locking object
	private final static Object s_lockObject = new Object();

	// Instance
	private static MessageUtil s_instance = null;

	// Get the HIE config
	private MpiClientConfiguration m_configuration = MpiClientConfiguration.getInstance();

	/**
	 * Creates a new message utility
	 */
	private MessageUtil() {
	}

	/**
	 * Get an instance of the message utility
	 */
	public static MessageUtil getInstance() {
		if (s_instance == null)
			synchronized (s_lockObject) {
				if (s_instance == null)
					s_instance = new MessageUtil();
			}
		return s_instance;
	}

	/**
	 * Send a HAPI message to the server and parse the response
	 * 
	 * @throws HL7Exception
	 * @throws IOException
	 * @throws LLPException
	 */
	public Message sendMessage(Message request, String endpoint, int port)
			throws HL7Exception, LLPException, IOException {
		PipeParser parser = new PipeParser();
		ConnectionHub hub = ConnectionHub.getInstance();
		Connection connection = null;
		try {
			if (log.isInfoEnabled())
				log.info(String.format("Sending to %s:%s : %s", endpoint, port, parser.encode(request)));

			connection = hub.attach(endpoint, port, parser, MinLowerLayerProtocol.class, this.m_configuration.getUseTls());

			Initiator initiator = connection.getInitiator();
			initiator.setTimeoutMillis(20000);
			Message response = initiator.sendAndReceive(request);

			if (log.isInfoEnabled())
				log.info(String.format("Response from %s:%s : %s", endpoint, port, parser.encode(response)));

			return response;
		} finally {
			if (connection != null)
				hub.discard(connection);
		}
	}

	/**
	 * Create a Patient ID XREF Query
	 * 
	 * @throws HL7Exception
	 */
	public Message createPdqMessage(Map<String, String> queryParameters) throws HL7Exception {
		QBP_Q21 message = new QBP_Q21();
		this.updateMSH(message.getMSH(), "QBP", "Q22");
		// What do these statements do?
		Terser terser = new Terser(message);

		// Set the query parmaeters
		int qpdRep = 0;
		for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
			String[] value = entry.getValue().split("~");
			for (String e : value) {
				terser.set(String.format("/QPD-3(%d)-1", qpdRep), entry.getKey());
				terser.set(String.format("/QPD-3(%d)-2", qpdRep++), e);
			}
		}

		terser.set("/QPD-1-1", "Q22");
		terser.set("/QPD-1-2", "Find Candidates");
		terser.set("/QPD-1-3", "HL7");
		terser.set("/QPD-2-1", UUID.randomUUID().toString());

		return message;
	}

	/**
	 * Create the admit patient message
	 * 
	 * @param patient
	 * @return
	 * @throws HL7Exception
	 * @throws MpiClientException 
	 * @throws RegexSyntaxException
	 */
	public Message createAdmit(Patient patient) throws HL7Exception, MpiClientException {

		ADT_A01 message = new ADT_A01();
		this.updateMSH(message.getMSH(), "ADT", "A04");
		this.updateSFT(message.getSFT());
		message.getEVN().getRecordedDateTime().getTime().setValue(new TS(Calendar.getInstance(), TS.MINUTE).toString());
		message.getMSH().getVersionID().getVersionID().setValue("2.3.1");
		message.getPV1().getPatientClass().setValue("I");
		// Move patient data to PID
		this.updatePID(message.getPID(), patient, false);

		for (Relationship rel : Context.getPersonService().getRelationshipsByPerson(patient)) {
			this.updateNK1(message.getNK1(message.getNK1Reps()), rel, false);
		}

		// Extensions
		Terser terser = new Terser(message);
		for (PersonAttribute pat : patient.getActiveAttributes()) {
			String extensionMap = this.m_configuration.getExtensionMap().get(pat.getAttributeType().getName());
			if (extensionMap == null)
				continue;

			String[] extensionData = extensionMap.split("\\?");

			// Get segment name
			String segmentName = extensionData[0].substring(0, extensionData[0].indexOf("-")),
					fieldSpec = extensionData[0].substring(extensionData[0].indexOf("-") + 1);
			String[] fieldIters = fieldSpec.split("-");
			// Get segment
			Structure[] segments = message.getAll(segmentName);
			Segment segment = null;
			if (message.isRepeating(segmentName))
				segment = (Segment) message.get(segmentName, segments.length);
			else
				segment = (Segment) message.get(segmentName);

			// Set the value
			Terser.set(segment, Integer.parseInt(fieldIters[0]), 0,
					fieldIters.length > 1 ? Integer.parseInt(fieldIters[1]) : 1, 1, pat.getValue());
			;
			Type[] fields = segment.getField(Integer.parseInt(fieldIters[0]));
			if (fields[0] instanceof XPN) {
				// rewrite for names
				String rewriteRule = this.m_configuration.getNameRewriteRule();
				if (rewriteRule != null && !rewriteRule.isEmpty())
					this.rewriteName((XPN) fields[0], rewriteRule, pat.getValue());
			}

			// Set the guard
			if (extensionData.length > 1) {
				String[] qdata = extensionData[1].split("=");
				Terser.set(segment, Integer.parseInt(qdata[0].split("-")[1]), 0, 1, 1, qdata[1]);
			}

		}

		for (int i = 0; i < message.getNK1Reps(); i++)
			message.getNK1(i).getSetIDNK1().setValue(String.format("%s", i + 1));
		return message;
	}

	/**
	 * Update SFT Segment
	 * 
	 * @param sft
	 * @throws DataTypeException
	 */
	private void updateSFT(SFT sft) throws DataTypeException {
		// sft.getSoftwareProductName().setValue(String.format("OpenMRS %s",
		// OpenmrsConstants.OPENMRS_VERSION));
		// sft.getSoftwareVendorOrganization().getOrganizationName().setValue("OpenMRS
		// Community");
	}

	/**
	 * Update the PID segment
	 * 
	 * @throws HL7Exception
	 * @throws MpiClientException 
	 * @throws RegexSyntaxException
	 */
	private void updatePID(PID pid, Patient patient, boolean localIdOnly) throws HL7Exception, MpiClientException {

		// Update the pid segment with data in the patient
		HashMap<String, String> exportIdentifiers = this.m_configuration.getLocalPatientIdentifierTypeMap();

		// PID-3
		if ("-".equals(this.m_configuration.getLocalPatientIdRoot())) {

			// Preferred domain
			String domain = this.m_configuration.getPreferredCorrelationDomain();
			if (domain == null || domain.isEmpty())
				throw new HL7Exception(
						"Cannot determine update correlation id, please set a preferred correlation identity domain");
			else {

				CX cx = pid.getPatientIdentifierList(pid.getPatientIdentifierList().length);

				for (PatientIdentifier patIdentifier : patient.getIdentifiers()) {
					String thisDomain = exportIdentifiers.get(patIdentifier.getIdentifierType().getName());
					if (domain.equals(thisDomain)) {
						this.updateCX(cx, patIdentifier, domain);
					}
				}
			}
		} else {
			if (this.m_configuration.getLocalPatientIdRoot().matches("^(\\d+?\\.){1,}\\d+$")) {
				pid.getPatientIdentifierList(0).getAssigningAuthority().getUniversalID()
						.setValue(this.m_configuration.getLocalPatientIdRoot());
				pid.getPatientIdentifierList(0).getAssigningAuthority().getUniversalIDType().setValue("ISO");
			} else
				pid.getPatientIdentifierList(0).getAssigningAuthority().getNamespaceID()
						.setValue(this.m_configuration.getLocalPatientIdRoot());

			pid.getPatientIdentifierList(0).getIDNumber().setValue(patient.getId().toString());
			pid.getPatientIdentifierList(0).getIdentifierTypeCode().setValue("PI");

			// Other identifiers
			if (!localIdOnly) {

				// Export IDs
				for (PatientIdentifier patIdentifier : patient.getIdentifiers()) {
					String domain = exportIdentifiers.get(patIdentifier.getIdentifierType().getName());
					if (domain != null) {
						CX cx = pid.getPatientIdentifierList(pid.getPatientIdentifierList().length);
						this.updateCX(cx, patIdentifier, domain);
					} else
						log.warn(String.format("Ignoring domain %s as it is not configured",
								patIdentifier.getIdentifierType().getName()));

				}
			}
		}
		// Names
		for (PersonName pn : patient.getNames())
			if (!pn.getFamilyName().equals("(none)") && !pn.getGivenName().equals("(none)"))
				this.updateXPN(pid.getPatientName(pid.getPatientName().length), pn);

		// Gender
		pid.getAdministrativeSex().setValue(patient.getGender());

		// Date of birth
		if (patient.getBirthdateEstimated())
			pid.getDateTimeOfBirth().getTime().setValue(new SimpleDateFormat("yyyy").format(patient.getBirthdate()));
		else
			pid.getDateTimeOfBirth().getTime()
					.setValue(new SimpleDateFormat("yyyyMMdd").format(patient.getBirthdate()));

		// Addresses
		for (PersonAddress pa : patient.getAddresses()) {
			XAD xad = pid.getPatientAddress(pid.getPatientAddress().length);
			this.updateXAD(xad, pa);
		}

		// Death?
		if (patient.getDead()) {
			pid.getPatientDeathIndicator().setValue("Y");
			pid.getPatientDeathDateAndTime().getTime().setDatePrecision(patient.getDeathDate().getYear(),
					patient.getDeathDate().getMonth(), patient.getDeathDate().getDay());
		}

		// Mother?
		for (Relationship rel : Context.getPersonService().getRelationships(patient, null, null)) {
			if (rel.getRelationshipType().getDescription().contains("MTH") && patient.equals(rel.getPersonB())) // MOTHER?
			{
				// TODO: Find a better ID
				this.updateXPN(pid.getMotherSMaidenName(0), rel.getPersonB().getNames().iterator().next());
				pid.getMotherSIdentifier(0).getAssigningAuthority().getUniversalID()
						.setValue(this.m_configuration.getLocalPatientIdRoot());
				pid.getMotherSIdentifier(0).getAssigningAuthority().getUniversalIDType().setValue("ISO");
				pid.getMotherSIdentifier(0).getIDNumber().setValue(String.format("%s", rel.getPersonB().getId()));
			}

		}

	}

	/**
	 * Update the CX
	 * 
	 * @param cx     The HL7 Composite
	 * @param id     The local OpenMRS Patient Identifier
	 * @param domain The domain to expose the identifier as
	 * @throws DataTypeException
	 */
	private void updateCX(CX cx, PatientIdentifier id, String domain) throws DataTypeException {
		if (domain.matches("^(\\d+?\\.){1,}\\d+$")) {
			cx.getAssigningAuthority().getUniversalID().setValue(domain);
			cx.getAssigningAuthority().getUniversalIDType().setValue("ISO");
		} else
			cx.getAssigningAuthority().getNamespaceID().setValue(domain);
		cx.getIDNumber().setValue(id.getIdentifier());
		cx.getIdentifierTypeCode().setValue("PT");

	}

	/**
	 * Update the NK1 segment
	 * 
	 * @throws HL7Exception
	 * @throws MpiClientException 
	 * @throws RegexSyntaxException
	 */
	private void updateNK1(NK1 nk1, Relationship relationship, boolean localIdOnly)
			throws HL7Exception, MpiClientException {

		// Update the pid segment with data in the patient
		Person person = relationship.getPersonA();

		// Type?
		// HACK:
		if (relationship.getRelationshipType() == null)
			nk1.getRelationship().getIdentifier().setValue("NOK");
		else if (relationship.getRelationshipType().getaIsToB().toLowerCase().equals("father"))
			nk1.getRelationship().getIdentifier().setValue("FTH");
		else if (relationship.getRelationshipType().getaIsToB().toLowerCase().equals("mother"))
			nk1.getRelationship().getIdentifier().setValue("MTH");
		else if (relationship.getRelationshipType().getaIsToB().toLowerCase().equals("parent"))
			nk1.getRelationship().getIdentifier().setValue("PAR");
		else
			nk1.getRelationship().getIdentifier().setValue("NOK");

		// Identity
		nk1.getNextOfKinAssociatedPartySIdentifiers(0).getAssigningAuthority().getUniversalID()
				.setValue(this.m_configuration.getLocalPatientIdRoot());
		nk1.getNextOfKinAssociatedPartySIdentifiers(0).getAssigningAuthority().getUniversalIDType().setValue("ISO");
		nk1.getNextOfKinAssociatedPartySIdentifiers(0).getIDNumber().setValue(person.getId().toString());
		nk1.getNextOfKinAssociatedPartySIdentifiers(0).getIdentifierTypeCode().setValue("PI");

		// Other identifiers
		if (!localIdOnly && relationship.getPersonB().isPatient()) {

			HashMap<String, String> exportIdentifiers = this.m_configuration.getLocalPatientIdentifierTypeMap();

			// Get the person as a patient
			Patient patient = Context.getPatientService().getPatient(relationship.getPersonB().getId());
			for (PatientIdentifier patIdentifier : patient.getIdentifiers()) {
				String domain = exportIdentifiers.get(patIdentifier.getIdentifierType().getName());
				if (domain != null) {
					CX cx = nk1.getNextOfKinAssociatedPartySIdentifiers(
							nk1.getNextOfKinAssociatedPartySIdentifiers().length);
					this.updateCX(cx, patIdentifier, domain);
				}

			}
		}

		// Names
		for (PersonName pn : person.getNames())
			if (!pn.getFamilyName().equals("(none)") && !pn.getGivenName().equals("(none)"))
				this.updateXPN(nk1.getNKName(nk1.getNKName().length), pn);

		// Gender
		nk1.getAdministrativeSex().setValue(person.getGender());

		// Date of birth
		if (person.getBirthdate() != null) {
			if (person.getBirthdateEstimated())
				nk1.getDateTimeOfBirth().getTime().setValue(new SimpleDateFormat("yyyy").format(person.getBirthdate()));
			else
				nk1.getDateTimeOfBirth().getTime()
						.setValue(new SimpleDateFormat("yyyyMMdd").format(person.getBirthdate()));
		}

		// Addresses
		for (PersonAddress pa : person.getAddresses()) {
			XAD xad = nk1.getContactPersonSAddress(nk1.getContactPersonSAddress().length);
			this.updateXAD(xad, pa);
		}

	}

	/**
	 * Updates the PN with the XPN
	 * 
	 * @param xpn
	 * @param pn
	 * @throws RegexSyntaxException
	 * @throws HL7Exception
	 * @throws MpiClientException 
	 */
	private void updateXPN(XPN xpn, PersonName pn) throws HL7Exception, MpiClientException {

		String nameRewrite = this.m_configuration.getNameRewriteRule();

		if (nameRewrite == null || nameRewrite.isEmpty()) {
			if (pn.getFamilyName() != null && !pn.getFamilyName().equals("(NULL)"))
				xpn.getFamilyName().getSurname().setValue(pn.getFamilyName());
			if (pn.getFamilyName2() != null)
				xpn.getFamilyName().getSurnameFromPartnerSpouse().setValue(pn.getFamilyName2());
			if (pn.getGivenName() != null && !pn.getGivenName().equals("(NULL)"))
				xpn.getGivenName().setValue(pn.getGivenName());
			if (pn.getMiddleName() != null)
				xpn.getSecondAndFurtherGivenNamesOrInitialsThereof().setValue(pn.getMiddleName());
			if (pn.getPrefix() != null)
				xpn.getPrefixEgDR().setValue(pn.getPrefix());
		} else {
			String nameString = String.format("%s %s %s %s %s", pn.getPrefix(), pn.getGivenName(), pn.getFamilyName(),
					pn.getFamilyName2(), pn.getFamilyNameSuffix()).replace("null ", "").replace(" null", "");
			this.rewriteName(xpn, nameRewrite, nameString);
		}

		if (pn.getPreferred())
			xpn.getNameTypeCode().setValue("L");
		else
			xpn.getNameTypeCode().setValue("U");

		this.log.info(String.format("XPN: %s", xpn.toString()));

	}

	/**
	 * Rewrites the specified name using the rewrite rule
	 * 
	 * @param xpn         The XPN to rewrite
	 * @param nameRewrite The way to rewrite the name
	 * @param nameString  The name string itself
	 * @throws RegexSyntaxException
	 * @throws HL7Exception
	 */
	public void rewriteName(XPN xpn, String nameRewrite, String nameString) throws MpiClientException, HL7Exception {

		String[] rules = nameRewrite.split("/");
		if (rules.length != 4 || !rules[0].isEmpty())
			throw new MpiClientException(String.format("%s is not valid regex", nameRewrite));

		if (rules[3].contains("i"))
			rules[1] = String.format("(?i)%s", rules[1]);
		String hl7Data = nameString.replaceAll(rules[1], rules[2]);

		this.log.info(String.format("MPI Will Rewrite name from %s to %s", nameString, hl7Data));

		// This version of HAPI doesn't have the nice parse function
		// HACK: Manually parse the input
		String[] comps = hl7Data.split("\\^");
		for (int c = 0; c < comps.length; c++) {
			String[] subComps = comps[c].split("&");
			Object hl7Comp = xpn.getComponent(c);
			if (hl7Comp instanceof AbstractPrimitive)
				((AbstractPrimitive) xpn.getComponent(c)).setValue(comps[c]);
			else
				for (int s = 0; s < subComps.length; s++) {
					((AbstractPrimitive) ((Composite) xpn.getComponent(c)).getComponent(s)).setValue(subComps[s]);
				}
		}

	}

	/**
	 * Updates the specified HL7v2 address
	 * 
	 * @param xad
	 * @param pa
	 * @throws DataTypeException
	 */
	private void updateXAD(XAD xad, PersonAddress pa) throws DataTypeException {
		if (pa.getAddress1() != null)
			xad.getStreetAddress().getStreetOrMailingAddress().setValue(pa.getAddress1());
		if (pa.getAddress2() != null)
			xad.getOtherDesignation().setValue(pa.getAddress2());
		if (pa.getCityVillage() != null)
			xad.getCity().setValue(pa.getCityVillage());
		if (pa.getCountry() != null)
			xad.getCountry().setValue(pa.getCountry());
		else if (this.m_configuration.getDefaultCountry() != null
				&& !this.m_configuration.getDefaultCountry().isEmpty())
			xad.getCountry().setValue(this.m_configuration.getDefaultCountry());
		if (pa.getCountyDistrict() != null)
			xad.getCountyParishCode().setValue(pa.getCountyDistrict());
		if (pa.getPostalCode() != null)
			xad.getZipOrPostalCode().setValue(pa.getPostalCode());
		if (pa.getStateProvince() != null)
			xad.getStateOrProvince().setValue(pa.getStateProvince());

		if (pa.getPreferred())
			xad.getAddressType().setValue("L");
	}

	/**
	 * Update MSH
	 * 
	 * @param msh
	 * @throws DataTypeException
	 */
	private void updateMSH(MSH msh, String messageCode, String triggerEvent) throws DataTypeException {
		msh.getFieldSeparator().setValue("|");
		msh.getEncodingCharacters().setValue("^~\\&");
		msh.getAcceptAcknowledgmentType().setValue("AL"); // Always send response
		msh.getDateTimeOfMessage().getTime().setValue(new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())); // DateTime
																													// of
																													// message
		msh.getMessageControlID().setValue(UUID.randomUUID().toString()); // Unique id for message
		msh.getMessageType().getMessageStructure().setValue(msh.getMessage().getName()); // Message Structure Type
		msh.getMessageType().getMessageCode().setValue(messageCode); // Message Structure Code
		msh.getMessageType().getTriggerEvent().setValue(triggerEvent); // Trigger Event
		msh.getProcessingID().getProcessingID().setValue("P"); // Production
		msh.getReceivingApplication().getNamespaceID().setValue(this.m_configuration.getRemoteApplication()); // Client
																												// Registry
		msh.getReceivingFacility().getNamespaceID().setValue(this.m_configuration.getRemoteFacility()); // Mohawk
																										// College of
																										// Applied Arts
																										// and
																										// Technology

		if (this.m_configuration.getMsh8Security() != null && !this.m_configuration.getMsh8Security().isEmpty())
			msh.getSecurity().setValue(this.m_configuration.getMsh8Security());

		msh.getSendingApplication().getNamespaceID().setValue(this.m_configuration.getLocalApplication());
		msh.getSendingFacility().getNamespaceID().setValue(this.m_configuration.getLocalFacility());
		msh.getVersionID().getVersionID().setValue("2.5");
	}

	/**
	 * Interpret PID segments
	 * 
	 * @param response
	 * @return
	 * @throws HL7Exception
	 */
	public List<MpiPatient> interpretPIDSegments(Message response) throws HL7Exception, MpiClientException {
		List<MpiPatient> retVal = new ArrayList<MpiPatient>();

		Terser terser = new Terser(response);
		// Check for AA and OK in QAK
		if (terser.get("/MSA-1") != null && terser.get("/MSA-1").equals("AE"))
			throw new MpiClientException("Server Error");
		else if (terser.get("/QAK-2") != null && terser.get("/QAK-2").equals("NF"))
			return retVal;

		Location defaultLocation = Context.getLocationService().getDefaultLocation();

		Structure[] responseGroups = response.getAll("QUERY_RESPONSE");
		HashMap<String, String> extensions = this.m_configuration.getExtensionMap();
		// Iterate over segments
		for (int i = 0; i < responseGroups.length; i++) {
			Structure queryResponseStruct = responseGroups[i];
			Group queryResponseGroup = (Group) queryResponseStruct;
			MpiPatient patient = new MpiPatient();
			for (Structure pidStruct : queryResponseGroup.getAll("PID")) // Parsing PID
			{
				PID pid = (PID) pidStruct;

				// Attempt to load a patient by identifier
				for (CX id : pid.getPatientIdentifierList()) {
					// ID is a local identifier
					if (this.m_configuration.getLocalPatientIdRoot()
							.equals(id.getAssigningAuthority().getNamespaceID().getValue())
							|| this.m_configuration.getLocalPatientIdRoot()
									.equals(id.getAssigningAuthority().getUniversalID().getValue())) {
						if (StringUtils.isNumeric(id.getIDNumber().getValue()))
							patient.setId(Integer.parseInt(id.getIDNumber().getValue()));
						else {
							this.log.warn(String.format(
									"Patient identifier %s in %s claims to be from local domain but is not in a valid format",
									id.getIDNumber().getName(),
									id.getAssigningAuthority().getNamespaceID().getValue()));
							continue;
						}
					}
					// ID is the preferred correlation domain
					else if (this.m_configuration.getPreferredCorrelationDomain() != null
							&& !this.m_configuration.getPreferredCorrelationDomain().isEmpty()
							&& (this.m_configuration.getPreferredCorrelationDomain()
									.equals(id.getAssigningAuthority().getNamespaceID().getValue())
									|| this.m_configuration.getPreferredCorrelationDomain()
											.equals(id.getAssigningAuthority().getUniversalID().getValue()))) {

						PatientIdentifier patientIdentifier = this.interpretCx(id);
						List<PatientIdentifierType> identifierTypes = new ArrayList<PatientIdentifierType>();
						identifierTypes.add(patientIdentifier.getIdentifierType());
						// HACK: The old method of fetching patient by ID seems to no longer work in OMRS2 - So we'll use this method instead
						List<PatientIdentifier> matchPatientIds = Context.getPatientService().getPatientIdentifiers(patientIdentifier.getIdentifier(), identifierTypes, null, null, null);
						if (matchPatientIds != null && matchPatientIds.size() > 0)
							patient.setId(matchPatientIds.get(0).getPatient().getId());
						patient.addIdentifier(patientIdentifier);
					} else {
						PatientIdentifier patId = this.interpretCx(id);
						if (patId != null)
							patient.addIdentifier(patId);
					}
				}

				// Attempt to copy names
				for (XPN xpn : pid.getPatientName()) {

					PersonName pn = this.interpretXpn(xpn);
					patient.addName(pn);
				}

				// Copy gender
				patient.setGender(pid.getAdministrativeSex().getValue());

				// Copy DOB
				if (pid.getDateTimeOfBirth().getTime().getValue() != null) {
					TS tsTemp = TS.valueOf(pid.getDateTimeOfBirth().getTime().getValue());
					patient.setBirthdate(tsTemp.getDateValue().getTime());
					patient.setBirthdateEstimated(tsTemp.getDateValuePrecision() < TS.DAY);
				}

				// Death details
				if (pid.getPatientDeathDateAndTime().getTime().getValue() != null) {
					TS tsTemp = TS.valueOf(pid.getPatientDeathDateAndTime().getTime().getValue());

					patient.setDeathDate(tsTemp.getDateValue().getTime());
				}
				patient.setDead("Y".equals(pid.getPatientDeathIndicator().getValue()));

				// Addresses
				for (XAD xad : pid.getPatientAddress()) {
					// Skip bad addresses
					if ("BA".equals(xad.getAddressType()))
						continue;

					PersonAddress pa = this.interpretXad(xad);
					patient.addAddress(pa);
				}
			}
			if (Arrays.asList(queryResponseGroup.getNames()).contains("NK1")
					&& this.m_configuration.getUseOpenMRSRelationships()) // Parse NK1 if we're using OpenMRS
																			// relationships
				for (Structure nk1Struct : queryResponseGroup.getAll("NK1")) {
					NK1 nk1 = (NK1) nk1Struct;
					Person relatedPerson = new Person();
					Relationship relationship = new Relationship();
					relationship.setPersonB(patient);
					RelationshipType relType = Context.getPersonService()
							.getRelationshipTypeByName(nk1.getRelationship().getIdentifier().getValue());
					if (relType != null)
						relationship.setRelationshipType(relType);
					else if ("MTH".equals(nk1.getRelationship().getIdentifier().getValue())
							|| "FTH".equals(nk1.getRelationship().getIdentifier().getValue())) {
						relationship.setRelationshipType(
								Context.getPersonService().getRelationshipTypeByName("Parent/Child"));
					} else {
						this.log.warn(String.format(
								"Can't convey relationship %s. Please create a relationship type with this name",
								nk1.getRelationship().getIdentifier().getValue()));
					}

					// Attempt to load a patient by identifier
					for (CX id : nk1.getNextOfKinAssociatedPartySIdentifiers()) {
						// ID is a local identifier
						if (this.m_configuration.getLocalPatientIdRoot()
								.equals(id.getAssigningAuthority().getNamespaceID().getValue())
								|| this.m_configuration.getLocalPatientIdRoot()
										.equals(id.getAssigningAuthority().getUniversalID().getValue())) {
							if (StringUtils.isNumeric(id.getIDNumber().getValue()))
								relatedPerson.setId(Integer.parseInt(id.getIDNumber().getValue()));
							else {
								this.log.warn(String.format(
										"Patient identifier %s in %s claims to be from local domain but is not in a valid format",
										id.getIDNumber().getName(),
										id.getAssigningAuthority().getNamespaceID().getValue()));
								continue;
							}
						} else {
							relatedPerson = new Patient(relatedPerson.getId());
							PatientIdentifier patId = this.interpretCx(id);
							if (patId != null)
								((Patient) relatedPerson).addIdentifier(patId);
						}
					}

					// Attempt to copy names
					for (XPN xpn : nk1.getNKName()) {
						PersonName pn = this.interpretXpn(xpn);
						relatedPerson.addName(pn);
					}

					// Copy gender
					relatedPerson.setGender(nk1.getAdministrativeSex().getValue());

					// Copy DOB
					if (nk1.getDateTimeOfBirth().getTime().getValue() != null) {
						TS tsTemp = TS.valueOf(nk1.getDateTimeOfBirth().getTime().getValue());
						relatedPerson.setBirthdate(tsTemp.getDateValue().getTime());
						relatedPerson.setBirthdateEstimated(tsTemp.getDateValuePrecision() < TS.DAY);
					}

					// Addresses
					for (XAD xad : nk1.getContactPersonSAddress()) {
						// Skip bad addresses
						if ("BA".equals(xad.getAddressType()))
							continue;

						PersonAddress pa = this.interpretXad(xad);
						relatedPerson.addAddress(pa);
					}

					relationship.setPersonA(relatedPerson);

					patient.addRelationship(relationship);
				}

			// Set any custom extended values
			if (extensions.size() > 0) {

				// Map QUERY_RESPONSE objects to events
				for (String extName : extensions.keySet()) {

					PersonAttributeType pat = Context.getPersonService().getPersonAttributeTypeByName(extName);
					if (pat == null) {
						this.log.warn(String.format("Could not find extension type %s", extName));
						continue;
					}
					// Attempt
					String terse = String.format("/QUERY_RESPONSE(%s)", i);
					String filter = extensions.get(extName);
					String[] filterData = filter.split("\\?"); // ? is used as a filter for example: Father's
																// Name:NK1-2?NK1-3=MTH
					String segmentName = filterData[0].substring(0, filterData[0].indexOf("-"));
					if (!Arrays.asList(queryResponseGroup.getNames()).contains(segmentName))
						continue;
					terse += String.format("/%s", segmentName);

					// Filter has guard
					if (filterData.length > 1) {
						String[] guard = filterData[1].split("=");

						Structure[] childSegment = queryResponseGroup.getAll(guard[0].split("-")[0]);
						for (int si = 0; si < childSegment.length; si++) {
							int fieldNo = Integer.parseInt(guard[0].split("-")[1]);
							if (guard[1].equals(Terser.get((Segment) childSegment[si], fieldNo, 0, 1, 1))) {
								terse += String.format("(%s)", si);
							}
						}
					}

					terse += String.format("-%s", filterData[0].substring(filterData[0].indexOf("-") + 1));

					// Get the value
					String value = terser.get(terse);
					if (value != null && !value.isEmpty())
						patient.addAttribute(new PersonAttribute(pat, value));
				}
			}
			retVal.add(patient);
		}

		return retVal;
	}

	/**
	 * Interpret the XAD as a person address
	 * 
	 * @param xad
	 * @return
	 */
	private PersonAddress interpretXad(XAD xad) {
		PersonAddress pa = new PersonAddress();

		// Set the address
		pa.setAddress1(xad.getStreetAddress().getStreetOrMailingAddress().getValue());
		pa.setAddress2(xad.getOtherDesignation().getValue());
		pa.setCityVillage(xad.getCity().getValue());
		pa.setCountry(xad.getCountry().getValue());
		pa.setCountyDistrict(xad.getCountyParishCode().getValue());
		pa.setPostalCode(xad.getZipOrPostalCode().getValue());
		pa.setStateProvince(xad.getStateOrProvince().getValue());

		if ("H".equals(xad.getAddressType().getValue()))
			pa.setPreferred(true);

		return pa;
	}

	/**
	 * Interpret the XPN as a Patient Name
	 * 
	 * @param pn The XPN to interpret
	 * @return The interpreted name
	 */
	private PersonName interpretXpn(XPN xpn) {
		PersonName pn = new PersonName();

		if (xpn.getFamilyName().getSurname().getValue() == null
				|| xpn.getFamilyName().getSurname().getValue().isEmpty())
			pn.setFamilyName("(NULL)");
		else
			pn.setFamilyName(xpn.getFamilyName().getSurname().getValue());
		pn.setFamilyName2(xpn.getFamilyName().getSurnameFromPartnerSpouse().getValue());

		// Given name
		if (xpn.getGivenName().getValue() == null || xpn.getGivenName().getValue().isEmpty())
			pn.setGivenName("(NULL)");
		else
			pn.setGivenName(xpn.getGivenName().getValue());

		pn.setMiddleName(xpn.getSecondAndFurtherGivenNamesOrInitialsThereof().getValue());
		pn.setPrefix(xpn.getPrefixEgDR().getValue());

		if ("L".equals(xpn.getNameTypeCode().getValue()))
			pn.setPreferred(true);

		return pn;
	}

	/**
	 * Interpret the CX ID as a patient Identifier
	 * 
	 * @param id
	 */
	private PatientIdentifier interpretCx(CX id) {

		PatientIdentifierType pit = null;
		HashMap<String, String> authorityMaps = this.m_configuration.getLocalPatientIdentifierTypeMap();

		// Get the domain we're lookng for
		String[] domains = new String[] { id.getAssigningAuthority().getNamespaceID().getValue(),
				id.getAssigningAuthority().getUniversalID().getValue() };

		// Is there a map for this object?
		for (String domain : domains) {

			if (domain == null)
				continue;

			for (String key : authorityMaps.keySet()) {
				if (domain.equals(authorityMaps.get(key))) {
					pit = Context.getPatientService().getPatientIdentifierTypeByName(key);
					if (pit == null)
						this.log.warn(String.format("%s is mapped to %s but cannot find %s", domain, key, key));
				}
			}

			// No map, so we should try to fa
			if (pit == null) {
				pit = Context.getPatientService().getPatientIdentifierTypeByName(domain);
				if (pit == null)
					pit = Context.getPatientService().getPatientIdentifierTypeByUuid(domain);
				if (pit != null && !pit.getUuid().equals(id.getAssigningAuthority().getUniversalID().getValue())
						&& this.m_configuration.getAutoUpdateLocalPatientIdentifierTypes()) // fix the UUID
				{
					log.debug(String.format("Updating %s to have UUID %s", pit.getName(),
							id.getAssigningAuthority().getUniversalID().getValue()));
					pit.setUuid(id.getAssigningAuthority().getUniversalID().getValue());
					Context.getPatientService().savePatientIdentifierType(pit);
				}
			}
		}

		if (pit == null && !this.m_configuration.getEnterprisePatientIdRoot()
				.equals(id.getAssigningAuthority().getNamespaceID().getValue())) {
			this.log.warn(String.format("ID domain %s has no known mapping to a Patient ID type",
					id.getAssigningAuthority().getNamespaceID().getValue()));
			return null;
		}

		PatientIdentifier patId = new PatientIdentifier(id.getIDNumber().getValue(), pit, null);

		// Do not include the local identifier
		if (id.getAssigningAuthority().getNamespaceID().equals(this.m_configuration.getNationalPatientIdRoot()))
			patId.setPreferred(true);

		return patId;
	}

	/**
	 * Create a PIX message
	 * 
	 * @throws HL7Exception
	 */
	public Message createPixMessage(Patient patient, String... toAssigningAuthorities) throws HL7Exception {
		QBP_Q21 retVal = new QBP_Q21();
		this.updateMSH(retVal.getMSH(), "QBP", "Q23");
		retVal.getMSH().getVersionID().getVersionID().setValue("2.5");

		Terser queryTerser = new Terser(retVal);

		// Determine if the ID root is an OID or name
		if (!this.m_configuration.getPreferredCorrelationDomain().isEmpty()) {

			// Affix the patient's preferred correlation domain identifier
			for (PatientIdentifier pid : patient.getIdentifiers()) {
				String domainName = this.m_configuration.getLocalPatientIdentifierTypeMap()
						.get(pid.getIdentifierType().getName());

				if (this.m_configuration.getPreferredCorrelationDomain().equals(domainName)) // This is the identifier
																								// we want
				{
					queryTerser.set("/QPD-3-1", pid.getIdentifier());
					break;
				}

			}

			if (queryTerser.get("/QPD-3-1") == null || queryTerser.get("/QPD-3-1").isEmpty()) {
				log.error(String.format(
						"Patient with id %s does not have any identifier in domain %s . It is unsafe to send this message to the central MPI, therefore no reuslts will be retured. Please ensure that your correlation identifier is mandatory for all patients",
						patient.getId(), this.m_configuration.getPreferredCorrelationDomain()));
				queryTerser.set("/QPD-3-1", "XX-NORETURN--XX-" + UUID.randomUUID().toString()); // HACK: This should
																								// throw some sort of
																								// error, however in
																								// lieu of this we'll
																								// log and guarantee
																								// that no results come
																								// back
			}

			if (this.m_configuration.getPreferredCorrelationDomain().matches("^(\\d+?\\.){1,}\\d+$")) {
				queryTerser.set("/QPD-3-4-2", this.m_configuration.getPreferredCorrelationDomain());
				queryTerser.set("/QPD-3-4-3", "ISO");
			} else
				queryTerser.set("/QPD-3-4-1", this.m_configuration.getPreferredCorrelationDomain());
		} else {
			queryTerser.set("/QPD-3-1", patient.getId().toString());
			if (this.m_configuration.getLocalPatientIdRoot().matches("^(\\d+?\\.){1,}\\d+$")) {
				queryTerser.set("/QPD-3-4-2", this.m_configuration.getLocalPatientIdRoot());
				queryTerser.set("/QPD-3-4-3", "ISO");
			} else
				queryTerser.set("/QPD-3-4-1", this.m_configuration.getLocalPatientIdRoot());
		}

		int rep = 0;
		for (String toAssigningAuthority : toAssigningAuthorities) {
			// To domain
			if (toAssigningAuthority.matches("^(\\d+?\\.){1,}\\d+$")) {
				queryTerser.set(String.format("/QPD-4(%s)-4-2", rep), toAssigningAuthority);
				queryTerser.set(String.format("/QPD-4(%s)-4-3", rep), "ISO");
			} else
				queryTerser.set(String.format("/QPD-4(%s)-4-1", rep), toAssigningAuthority);
			rep++;
		}
		return retVal;
	}

	/**
	 * Create the update message
	 * 
	 * @throws HL7Exception
	 * @throws MpiClientException 
	 * @throws RegexSyntaxException
	 */
	public Message createUpdate(Patient patient) throws HL7Exception, MpiClientException {
		ADT_A01 message = new ADT_A01();
		this.updateMSH(message.getMSH(), "ADT", "A08");
		message.getMSH().getVersionID().getVersionID().setValue("2.3.1");

		// Move patient data to PID
		this.updatePID(message.getPID(), patient, true);

		return message;
	}
	
}
