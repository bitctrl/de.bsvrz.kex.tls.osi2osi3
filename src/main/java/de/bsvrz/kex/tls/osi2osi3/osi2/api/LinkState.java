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

package de.bsvrz.kex.tls.osi2osi3.osi2.api;

/**
 * Definiert die m�glichen Zust�nde einer Verbindung.
 *
 * @author Kappich Systemberatung
 * @version $Revision: 5114 $
 * @see DataLinkLayer.Link
 */
public class LinkState {

	/** Stabiler Zustand f�r eine nicht aufgebaute Verbindung. */
	public static final LinkState DISCONNECTED = new LinkState("Nicht Verbunden");

	/** �bergangszustand f�r eine im Aufbau befindliche Verbindung. */
	public static final LinkState CONNECTING = new LinkState("Verbindung wird aufgebaut");

	/** Stabiler Zustand f�r eine aufgebaute Verbindung. */
	public static final LinkState CONNECTED = new LinkState("Verbunden");

	/** �bergangszustand f�r eine im Abbau befindliche Verbindung. */
	public static final LinkState DISCONNECTING = new LinkState("Verbindung wird abgebaut");

	/** Name des Verbindungszustands */
	private final String _name;

	/**
	 * Liefert eine textuelle Beschreibung dieses Verbindungszustands zur�ck. Das genaue Format ist nicht festgelegt und kann sich �ndern.
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
	private LinkState(String name) {
		_name = name;
	}
}
