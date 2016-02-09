/*
 * Copyright 2007 by Kappich Systemberatung, Aachen
 * Copyright 2004 by Kappich+Kni� Systemberatung, Aachen
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

package de.bsvrz.kex.tls.osi2osi3.osi2.wancom;


/**
 * Definiert die m�glichen Zust�nde eines Protokolls.
 *
 * @author Kappich Systemberatung
 * @version $Revision: 5114 $
 */
public final class ProtocolState {

	/** Stabiler Zustand f�r ein noch nicht gestartetes Protokoll. */
	public static final ProtocolState CREATED = new ProtocolState("Erzeugt");

	/** �bergangszustand f�r ein startendes Protokoll. */
	public static final ProtocolState STARTING = new ProtocolState("Wird gestartet");

	/** Stabiler Zustand f�r ein gestartetes Protokoll. */
	public static final ProtocolState STARTED = new ProtocolState("Gestartet");

	/** �bergangszustand f�r ein zu stoppendes Protokoll. */
	public static final ProtocolState STOPPING = new ProtocolState("Wird gestoppt");

	/** Stabiler Zustand f�r ein gestopptes Protokoll. */
	public static final ProtocolState STOPPED = new ProtocolState("Gestoppt");

	/** Name des Zustands */
	private final String _name;

	/**
	 * Liefert eine textuelle Beschreibung dieses Zustands zur�ck. Das genaue Format ist nicht festgelegt und kann sich �ndern.
	 *
	 * @return Beschreibung dieses Zustands.
	 */
	public String toString() {
		return _name;
	}

	/**
	 * Nicht �ffentlicher Konstruktor der zum Erzeugen der vordefinierten Zust�nde benutzt wird.
	 *
	 * @param name Name des Zustandes.
	 */
	private ProtocolState(String name) {
		_name = name;
	}
}
