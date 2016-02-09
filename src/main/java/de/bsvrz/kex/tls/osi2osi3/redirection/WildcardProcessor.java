/*
 * Copyright 2009 by Kappich Systemberatung, Aachen
 * 
 * This file is part of de.bsvrz.kex.tls.osi2osi3.
 * 
 * de.bsvrz.kex.tls.osi2osi3 is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * de.bsvrz.kex.tls.osi2osi3 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with de.bsvrz.kex.tls.osi2osi3; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package de.bsvrz.kex.tls.osi2osi3.redirection;

import de.bsvrz.dav.daf.main.Data;
import de.bsvrz.dav.daf.main.Data.Array;
import de.bsvrz.dav.daf.main.Data.NumberValue;
import de.bsvrz.dav.daf.main.Data.ReferenceArray;
import de.bsvrz.dav.daf.main.config.SystemObject;
import de.bsvrz.sys.funclib.debug.Debug;

import java.util.*;

/**
 * Klasse zur Auswertung des OSI-3 Umleitungsparameters. Der Wildcardprozessor erzeugt ein Objekt der Klasse RedirectionInfo und initialisiert die Maps Receive-
 * und Send Entries durch die entsprechenden Funktionen.
 * 
 * @author Kappich Systemberatung
 * @version $Revision: 10172 $
 * 
 */
public class WildcardProcessor {
	
	/**
	 * TlsModell, aus dem die Konfigurationsinformationen (Informationen zu den Tls-Ger�ten) bezogen werden.
	 */
	private final TlsModel _tlsModel;

	private static final Debug _debug = Debug.getLogger();

	
	public WildcardProcessor(final TlsModel tlsModel) {
		if(tlsModel == null) throw new IllegalArgumentException("TlsModel ist null");
		_tlsModel = tlsModel;
	}
	
	
	/**
	 * Erzeugung einer neuen RedirectionInfo. Der �bergebene Parameter wird ausgewertet und es werden die neuen Strukturen aufgebaubt, �ber die abgefragt werden
	 * kann, ob bestimmte zu sendende oder empfangene Telegramme auch an andere Knoten weitergeleitet werden sollen.
	 * 
	 * 
	 * @param osi3RedirectionParameter
	 */
	RedirectionInfo createRedirectionInfo(Data osi3RedirectionParameter) {
		if(osi3RedirectionParameter == null) {
			throw new IllegalArgumentException("Der Parameter darf nicht null sein!");
		}
		_debug.info("Parameter erhalten, Auswertung starten und neues Redirectionobjekt erzeugen");
		RedirectionInfo redirectionInfo = new RedirectionInfo();
		
		Array incomingTelegrams = osi3RedirectionParameter.getArray("AnkommendeTelegramme");
		Array sendingTelegrams = osi3RedirectionParameter.getArray("ZuVersendendeTelegramme");
		
		HashSet<TlsNode> nodes = new HashSet<TlsNode>(); // Knoten, die ber�cksichtigt werden
		
		ReferenceArray devices; // Array des Absender oder Empf�nger (Muss vom Typ Ger�tReferenz sein).
		
		// Betrachte Ankommende Telegramme
		for(int i = 0; i < incomingTelegrams.getLength(); i++) {
			
			nodes.clear();
			
			Data sender = incomingTelegrams.getItem(i).getItem("Absender");
			devices = sender.getReferenceArray("TlsKnoten");
			// Alle explizit angegebenen Ger�te aufnehmen
			for(int j = 0; j < devices.getLength(); j++) {
				try {
					SystemObject device = devices.getReferenceValue(j).getSystemObject();
					TlsNode tlsNode = _tlsModel.getTlsNode(device);
					nodes.add(tlsNode);
				}
				catch(Exception e) {
					_debug.warning("Parameter f�r OSI3-Umleitung: Der Absender TlsKnoten mit dem Index " + j + " konnte nicht ermittelt werden und wird ignoriert", e);
				}
			}
			// Ist das Filter Suche spezifiziert?
			Array searchNodes = sender.getArray("Suche");
			if(searchNodes.getLength() > 0) evaluateSearchFilter(nodes, searchNodes);
			
			// Alle Knoten gesammelt des Feldes (Index i) gesammelt
			// Weiterleitungsargumente auswerten
			int[] fgs = incomingTelegrams.getItem(i).getItem("Weiterleitung").getArray("Funktionsgruppen").asScaledArray().getIntArray();
			
			NumberValue localRoutine = incomingTelegrams.getItem(i).getItem("Weiterleitung").getScaledValue("TelegrammLokalVerarbeiten");
			
			boolean normalProcessing = true;
			if(localRoutine.isState() && localRoutine.getState().getName().equals("Nein")) {
				normalProcessing = false;
			}
			
			// Zielknoten auswerten
			SystemObject[] targets = incomingTelegrams.getItem(i).getItem("Weiterleitung").getReferenceArray("ZielTlsKnoten").getSystemObjectArray();
			// Map f�llen

			ArrayList<Integer> integers = new ArrayList<Integer>();
			for(int j = 0; j < targets.length; j++) {
				try {
					int nodeNumber = _tlsModel.getTlsNode(targets[j]).getNodeNumber();
					integers.add(new Integer(nodeNumber));
				}
				catch(Exception e) {
					_debug.warning("Parameter f�r OSI3-Umleitung: Der ZielTlsKnoten mit dem Index " + j + " konnte nicht ermittelt werden und wird ignoriert", e);
				}
			}
			int[] destinations = new int[integers.size()];
			for(int j = 0; j < destinations.length; j++) {
				destinations[j] = integers.get(j).intValue();
			}

			// Konverter Klassenname
			String converterClassName = incomingTelegrams.getItem(i).getItem("Weiterleitung").getTextValue("Konverter").getText().trim();

			
			// F�r alle ermittelten Knoten entsprechende Weiterleitungsinformationen schreiben
			for(TlsNode tlsNode : nodes) {
				int knr = tlsNode.getNodeNumber();
				// Funktionsgruppen betrachten
				if(fgs.length == 0) {
					// Jeder Eintrag erh�lt seinen eigenen Konverter
					Osi7SingleTelegramConverter converter = converterForName(converterClassName);
					if (converter!=null){
						converter.setTlsNode(tlsNode);
					}
					redirectionInfo.addReceiveEntry(knr, 255, normalProcessing, destinations, converter);
					_debug.fine("Alle FGs sollen betrachtet werden. Erg�nze ReceiveEntry: Knotennummer (" + knr + 
							    "),normalProcessing " + normalProcessing + " Ziele " + intArrayToLine(destinations) +
							    " Konverter " + converter
					        );
				}
				else {
					for(int j = 0; j < fgs.length; j++) {
						int fg = fgs[j];
						// Check, ob der Knoten �berhaupt die FG unterst�tzt.
						// Falls nicht, muss kein Eintrag get�tigt werden	            		
						if(tlsNode.hasFg(fg)) {
							Osi7SingleTelegramConverter converter = converterForName(converterClassName);
							if (converter!=null){
								converter.setTlsNode(tlsNode);
							}
							redirectionInfo.addReceiveEntry(knr, fg, normalProcessing, destinations, converter);
							_debug.fine("Erg�nze ReceiveEntry: Knotennummer (" + knr + "), FG " + fgs[j] + " normalProcessing " + normalProcessing+ " Ziele " + intArrayToLine(destinations) +
								    " Konverter " + converter);
						}
					}
				}
			}
		}
		
		// Betrachte zu versendende Telegramme
		for(int i = 0; i < sendingTelegrams.getLength(); i++) {
			nodes.clear();
			Data receiver = sendingTelegrams.getItem(i).getItem("Empf�nger");
			devices = receiver.getReferenceArray("TlsKnoten");
			// Alle explizit angegebenen Ger�te aufnehmen
			for(int j = 0; j < devices.getLength(); j++) {
				try {
					SystemObject device = devices.getReferenceValue(j).getSystemObject();
					TlsNode tlsNode = _tlsModel.getTlsNode(device);
					nodes.add(tlsNode);
				}
				catch(Exception e) {
					_debug.warning("Parameter f�r OSI3-Umleitung: Der Empf�nger TlsKnoten mit dem Index " + j + " konnte nicht ermittelt werden und wird ignoriert", e);
				}
			}
			// Ist das Filter Suche spezifiziert?
			Array searchNodes = receiver.getArray("Suche");
			if(searchNodes.getLength() > 0) evaluateSearchFilter(nodes, searchNodes);
			
			// Alle Knoten gesammelt des Feldes (Index i) gesammelt
			// Weiterleitungsargumente auswerten
			int[] fgs = sendingTelegrams.getItem(i).getItem("Weiterleitung").getArray("Funktionsgruppen").asScaledArray().getIntArray();
			NumberValue localRoutine = sendingTelegrams.getItem(i).getItem("Weiterleitung").getScaledValue("TelegrammAnEmpf�ngerSenden");
			boolean normalProcessing = true;
			if(localRoutine.isState() && localRoutine.getState().getName().equals("Nein")) {
				normalProcessing = false;
			}
			
			// Allgemeine Zielknoten auswerten
			SystemObject[] targets = sendingTelegrams.getItem(i).getItem("Weiterleitung").getReferenceArray("ZielTlsKnoten").getSystemObjectArray();
			List<Integer> destinationList = new ArrayList<Integer>();
			for(int j = 0; j < targets.length; j++) {
				try {
					int nodeNumber = _tlsModel.getTlsNode(targets[j]).getNodeNumber();
					destinationList.add(nodeNumber);
				}
				catch(Exception e) {
					_debug.warning("Parameter f�r OSI3-Umleitung: Der ZielTlsKnoten mit dem Index " + j + " konnte nicht ermittelt werden und wird ignoriert", e);
				}
			}

			// Eventuel sp�ter weitere Knoten aufnehmen, wenn eine Referenz auf den Typ eines �bergeordneten Zielknotens angegeben wurde.
			SystemObject typeSuperiorTlsNode = sendingTelegrams
			        .getItem(i)
			        .getItem("Weiterleitung")
			        .getReferenceValue("TypDes�bergeordnetenTlsKnoten")
			        .getSystemObject();
			
			
			// Konverter angegeben
			Osi7SingleTelegramConverter converter = null;
			String converterClassName = sendingTelegrams.getItem(i).getItem("Weiterleitung").getTextValue("Konverter").getText().trim();
			_debug.fine("converterClassName " + converterClassName);
			if(!converterClassName.equals("")){
				try {
					Class<Osi7SingleTelegramConverter> converterClass = (Class<Osi7SingleTelegramConverter>)Class.forName(converterClassName);
					converter = converterClass.newInstance();
				}
				catch(ClassNotFoundException e) {
					_debug.warning("Folgende Konverterklasse wurde nicht gefunden", converterClassName);
				}
                catch(InstantiationException e) {
					_debug.warning("Folgende Konverterklasse wurde nicht gefunden", converterClassName);
                }
                catch(IllegalAccessException e) {
					_debug.warning("Folgende Konverterklasse wurde nicht gefunden", converterClassName);
               }
			}
			
			// F�r alle ermittelten Knoten entsprechende Weiterleitungsinformationen schreiben
			for(TlsNode tlsNode : nodes) {
				int knr = tlsNode.getNodeNumber();
				
				int[] destinations = null;
				TlsNode superiorNode = null;

				if(typeSuperiorTlsNode != null) {
					superiorNode = _tlsModel.getSuperiorNodeOfType(tlsNode, typeSuperiorTlsNode);
//					if(superiorNode != null) {
//						destinationList.add(superiorNode.getNodeNumber());
//					}
				}

				if (superiorNode != null) {
					destinations = new int[destinationList.size()+1];
				}
				else{
					destinations = new int[destinationList.size()];
				}
				for(int j = 0; j < destinationList.size(); j++) {
					destinations[j] = destinationList.get(j);
				}
				if (superiorNode != null) destinations[destinationList.size()] = superiorNode.getNodeNumber();
				
				
				// Funktionsgruppen betrachten
				if(fgs.length == 0) {
					if (converter!=null){
						converter.setTlsNode(tlsNode);
					}
					redirectionInfo.addSendEntry(knr, 255, normalProcessing, destinations,converter);
					_debug.fine("Alle FGs sollen betrachtet werden. Erg�nze SendEntry: Knotennummer (" + knr + "),normalProcessing "
					        + normalProcessing);
				}
				else {
					for(int j = 0; j < fgs.length; j++) {
						int fg = fgs[j];
						// Check, ob der Knoten �berhaupt die FG unterst�tzt.
						// Falls nicht, muss kein Eintrag get�tigt werden	            		
						if(tlsNode.hasFg(fg)) {
							if (converter!=null){
								converter.setTlsNode(tlsNode);
							}
							redirectionInfo.addSendEntry(knr, fg, normalProcessing, destinations,converter);
							_debug.fine("Erg�nze addSendEntry: Knotennummer (" + knr + "), FG " + fgs[j] + " normalProcessing " + normalProcessing);
						}
					}
				}
			}
			
			
//			// Eventuel weitere Knoten aufnehmen, wenn eine Referenz auf den Typ eines �bergeordneten Zielknotens angegeben wurde.
//			SystemObject typeSuperiorTlsNode = sendingTelegrams
//			        .getItem(i)
//			        .getItem("Weiterleitung")
//			        .getReferenceValue("TypDes�bergeordnetenTlsKnoten")
//			        .getSystemObject();
//			if(typeSuperiorTlsNode != null) {
//				// �ber alle gefundenen Knoten
//				for(TlsNode tlsNode : nodes) {
//					TlsNode superiorNode = _tlsModel.getSuperiorNodeOfType(tlsNode, typeSuperiorTlsNode);
//					if(superiorNode != null) {
//						destinationList.add(superiorNode.getNodeNumber());
//					}
//				}
//			}
//			int[] destinations = new int[destinationList.size()];
//			for(int j = 0; j < destinations.length; j++) {
//				destinations[j] = destinationList.get(j);
//			}
//
//			// Konverter angegeben
//			Osi7SingleTelegramConverter converter = null;
//			String converterClassName = sendingTelegrams.getItem(i).getItem("Weiterleitung").getTextValue("Konverter").getText().trim();
//			_debug.fine("converterClassName " + converterClassName);
//			if(!converterClassName.equals("")){
//				try {
//					Class<Osi7SingleTelegramConverter> converterClass = (Class<Osi7SingleTelegramConverter>)Class.forName(converterClassName);
//					converter = converterClass.newInstance();
//				}
//				catch(ClassNotFoundException e) {
//					_debug.warning("Folgende Konverterklasse wurde nicht gefunden", converterClassName);
//				}
//                catch(InstantiationException e) {
//					_debug.warning("Folgende Konverterklasse wurde nicht gefunden", converterClassName);
//                }
//                catch(IllegalAccessException e) {
//					_debug.warning("Folgende Konverterklasse wurde nicht gefunden", converterClassName);
//               }
//			}
//			
//			
//			// F�r alle ermittelten Knoten entsprechende Weiterleitungsinformationen schreiben
//			for(TlsNode tlsNode : nodes) {
//				int knr = tlsNode.getNodeNumber();
//				// Funktionsgruppen betrachten
//				if(fgs.length == 0) {
//					redirectionInfo.addSendEntry(knr, 255, normalProcessing, destinations,converter);
//					_debug.fine("Alle FGs sollen betrachtet werden. Erg�nze SendEntry: Knotennummer (" + knr + "),normalProcessing "
//					        + normalProcessing);
//				}
//				else {
//					for(int j = 0; j < fgs.length; j++) {
//						int fg = fgs[j];
//						// Check, ob der Knoten �berhaupt die FG unterst�tzt.
//						// Falls nicht, muss kein Eintrag get�tigt werden	            		
//						if(tlsNode.hasFg(fg)) {
//							redirectionInfo.addSendEntry(knr, fg, normalProcessing, destinations,converter);
//							_debug.fine("Erg�nze addSendEntry: Knotennummer (" + knr + "), FG " + fgs[j] + " normalProcessing " + normalProcessing);
//						}
//					}
//				}
//			}
		}
		
		return redirectionInfo;
	}
	
	Osi7SingleTelegramConverter converterForName(String converterClassName){
		if(converterClassName.equals("") || converterClassName.equals("_Undefiniert_")) return null;

		Osi7SingleTelegramConverter converter = null;

		try {
			Class<Osi7SingleTelegramConverter> converterClass = (Class<Osi7SingleTelegramConverter>)Class.forName(converterClassName);
			converter = converterClass.newInstance();
			
		}
		catch(Exception e) {
			_debug.warning("Folgende Konverterklasse wurde nicht gefunden " + converterClassName ,e);
		}
		
		return converter;
	}
	
	String intArrayToLine(int[] array){
		String result = " (";
		if(array.length>0) result += array[0];
		for(int i = 1; i < array.length; i++) {
	        result += ", " + array[i];
        }
		result += ") ";
		return result;	
	}
	
	/**
	 * Methode zur Auswertung der Suchfilter. Hierbei erfolgt die Spezifikation des Filter �ber die Attributliste atl.spezifikationSucheGer�te. Diese Daten
	 * werden als Array �bergeben.
	 * 
	 * @param nodes
	 *            Hash, in dem die zu behandelnden Tls-Knoten gespeichert sind
	 * @param searchItems
	 *            Array mit Datens�tzen zur Attributliste atl.spezifikationSucheGer�te
	 */
	private void evaluateSearchFilter(HashSet<TlsNode> nodes, Array searchItems) {
		HashSet<TlsNode> exclusiveNode = new HashSet<TlsNode>(); // Exklusive Knoten
		exclusiveNode.clear();
		// Zur Zeit ist im Datenkatalog die maximale Anzahl auf 1 beschr�nkt
		for(int k = 0; k < searchItems.getLength(); k++) {
			// Exclusive Knoten aufnehmen
			ReferenceArray exclusiveSystemobjects = searchItems.getItem(k).getReferenceArray("Auszuschlie�endeTlsKnoten");
			for(int l = 0; l < exclusiveSystemobjects.getLength(); l++) {
				try {
					SystemObject device = exclusiveSystemobjects.getReferenceValue(l).getSystemObject();
					exclusiveNode.add(_tlsModel.getTlsNode(device));
				}
				catch(Exception e) {
					_debug.warning("Parameter f�r OSI3-Umleitung: Der Auszuschlie�endeTlsKnoten mit dem Index " + l + " konnte nicht ermittelt werden und wird ignoriert", e);
				}
			}
			// Filter auswerten
			SystemObject[] superiorSystemobjects = searchItems.getItem(k).getReferenceArray("�bergeordneteTlsKnoten").getSystemObjectArray();
			SystemObject typReference = searchItems.getItem(k).getReferenceValue("TypDerGesuchtenTlsKnoten").getSystemObject();
			int forcedFg = searchItems.getItem(k).getScaledValue("ErforderlicheFG").intValue();
			
			if(superiorSystemobjects.length > 0) {
				for(SystemObject systemObject : superiorSystemobjects) {
					Collection<TlsNode> tlsNodes = _tlsModel.getTlsNodes(systemObject, typReference, forcedFg);
					for(TlsNode tlsNode : tlsNodes) {
						if(!exclusiveNode.contains(tlsNode)) {
							nodes.add(tlsNode);
						}
					}
				}
			}
			// Alle untergeordneten Ger�te betrachten
			else {
				Collection<TlsNode> tlsNodes = _tlsModel.getTlsNodes(null, typReference, forcedFg);
				for(TlsNode tlsNode : tlsNodes) {
					if(!exclusiveNode.contains(tlsNode)) {
						nodes.add(tlsNode);
					}
				}
			}
		}
	}
	
}
