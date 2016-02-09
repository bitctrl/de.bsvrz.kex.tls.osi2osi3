/*
 * Copyright 2007 by Kappich Systemberatung, Aachen
 * Copyright 2005 by Kappich+Kni� Systemberatung Aachen (K2S)
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

package de.bsvrz.kex.tls.osi2osi3.longtelegram;

import de.bsvrz.sys.funclib.debug.Debug;

import java.util.*;

/**
 * Diese Klasse stellt ein Objekt zur Verf�gung, um Langtelegramm zu verarbeiten.
 *
 * @author Kappich Systemberatung
 * @version $Revision: 5114 $
 */
public class Osi7LongTelegramRecombine {

	/** Diese Map speichert alle Empf�nger. Der Empf�nger "baut" sich aus den erhaltenen Telegrammen ein Langtelegramm zusammen. */
	private final Map<ReceiverKey, Receiver> _dataReceivers = new HashMap<ReceiverKey, Receiver>();

	/** DebugLogger f�r Debug-Ausgaben */
	private static final Debug _debug = Debug.getLogger();

	/**
	 * Erstellt ein Langtelegramm. Teilst�cke werden an das bestehende Langtelegramm eingef�gt.
	 *
	 * @param telegram Teil eines Langtelegramms, das eingef�gt werden soll. Dies kann auch den Anfang eines Langtelegramms darstellen
	 *
	 * @return true = Das Langtelegramm ist fertig und kann angfordert werden; false = Es fehlen noch St�cke, damit das Langtelegramm fertiggestellt werden kann
	 */
	public boolean telegramReceived(byte[] telegram) {
		final ReceiverKey key = new ReceiverKey(telegram);
		// Empf�nger anfordern, falls kein Empf�nger vorhanden ist, wird ein Objekt angelegt.
		final Receiver receiver = getReceiver(key);
		receiver.newData(telegram);
		// Wurde das Telegramm fertiggestellt
		return receiver.isTelegramFinished();
	}

	/**
	 * Gibt ein fertiges Langtelegramm zur�ck, damit das fertige Langtelegramm gefunden werden kann, wird das letzte Teiltelegramm �bergeben.
	 *
	 * @param telegram Ein Teiltelegramm, das zu dem Langtelegramm geh�rt. Aus diesem Telegramm werden Informationen ausgelesen, um das richtige Langtelegramm zu
	 *                 finden.
	 *
	 * @return fertiges Langtelegramm
	 *
	 * @throws IllegalStateException Das angeforderte Langtelegramm war noch nicht fertig
	 */
	public byte[] getLongTelegram(byte[] telegram) throws IllegalStateException {
		final ReceiverKey key = new ReceiverKey(telegram);
		final Receiver receiver = getReceiver(key);
		return receiver.getLongTelegram();
	}

	/**
	 * Diese Methode pr�ft, ob ein Empf�nger vorhanden ist, wenn ja, dann wird dieser zur�ckgegeben. Falls kein Empf�nger vorhanden ist, wird dieser erzeugt und in
	 * der Map gespeichert.
	 *
	 * @param key Schl�ssel, zu dem ein Empf�nger gesucht wird. Ist kein Objekt f�r den Key vorhanden, wird ein Objekt angelegt und mit dem �bergebenen Key in der
	 *            Map gespeichert
	 *
	 * @return Empf�nger f�r Telegramme
	 */
	private Receiver getReceiver(ReceiverKey key) {
		synchronized(_dataReceivers) {
			if(_dataReceivers.containsKey(key)) {
				// Es ist ein Empf�nger vorhanden
				return _dataReceivers.get(key);
			}
			else {
				// Es ist kein empf�nger vorhanden, also wird ein Objekt angelegt und in der Map
				// gespeichert
				final Receiver newReceiver = new Receiver(key, this);
				_dataReceivers.put(key, newReceiver);
				return newReceiver;
			}
		}
	}

	/**
	 * Diese Methode entfernt einen Empf�nger aus der Map. Die Methode wird vom Empf�nger selbst aufgerufen, da nur das Empf�ngerobjekt weiss, dass es alle
	 * ben�tigten Telegramme erhalten hat.
	 *
	 * @param key Schl�ssel des Objekts, das entfernt werden soll
	 */
	private void removeReceiver(ReceiverKey key) {
		synchronized(_dataReceivers) {
			if(_dataReceivers.containsKey(key)) {
				// Es gibt ein Objekt zu dem Schl�ssel
				_dataReceivers.remove(key);
			}
			else {
				// Es soll ein Objekt entfernt werden, aber der Schl�ssel ist falsch
				throw new IllegalArgumentException(
						"Zu dem �bergebenen Schl�ssel: " + key + " wurde kein Objekt gefunden. Es wird kein Objekt entfernt."
				);
			}
		}
	}

	/**
	 * Diese Methode liest aus einem �bergebenen Langtelegramm den DatenBlockTyp aus.
	 *
	 * @param receivedData empfangendes Langtelegramm
	 *
	 * @return DatenblockTyp
	 */
	private static final int getDataBlockType(byte[] receivedData) {
		final int dataBlockTypeHelper = (receivedData[5] & 0xFF);
		return ((dataBlockTypeHelper & 0xF0) >> 4);
	}

	/**
	 * Diese Methode liest aus einem �bergebenen Langtelegramm die DatenBlockNummer aus.
	 *
	 * @param receivedData empfangendes Langtelegramm
	 *
	 * @return DatenBlockNummer
	 */
	private static final int getDataBlockNumber(byte[] receivedData) {
		int dataBlockTypeNumberHelper = (receivedData[5] & 0xFF);
		return (dataBlockTypeNumberHelper & 0x0F);
	}

	/**
	 * Diese Methode �bergibt aus einem �bergebenen Langtelegramm die Absenderadresse aus.
	 *
	 * @param receivedData empfangendes Langtelegramm
	 *
	 * @return Absenderadresse
	 */
	private static final int getSenderAddress(byte[] receivedData) {
		// Mit dieser Variablen wird die Adresse zusammengestellt
		int senderAddressHelper = 0;
		// extra high Byte
		senderAddressHelper = senderAddressHelper | (receivedData[2] & 0xFF);
		senderAddressHelper = senderAddressHelper << 8;

		// high Byte
		senderAddressHelper = senderAddressHelper | (receivedData[1] & 0xFF);
		senderAddressHelper = senderAddressHelper << 8;

		// low Byte
		senderAddressHelper = senderAddressHelper | (receivedData[1] & 0xFF);

		return senderAddressHelper;
	}


	private static final LongTelegramType getLongType(final byte[] receivedData) throws IllegalArgumentException {
		int typeInt = (receivedData[4] & 0xFF);
		typeInt = (typeInt & 0x0F);

		try {
			return LongTelegramType.getInstance(typeInt);
		}
		catch(IllegalArgumentException e) {
			throw new IllegalArgumentException("Der Long-Type eines Pakets kann nicht identifiziert werden: " + e);
		}
	}

	private final static class Receiver {

		/** Unter diesem Schl�ssel ist das Objekt in einer Map gespeichert */
		private final ReceiverKey _keyForThisReceiver;

		/**
		 * Dieses Objekt stellt eine Methode zur Verf�gung, mit dem sich dieses Objekt aus einer Map entfernen kann. Dies wird n�tig, wenn alle Daten empfangen
		 * wurden, oder ein Fehler aufgetreten ist.
		 */
		private final Osi7LongTelegramRecombine _receiverManager;

		/**
		 * In diesem Byte-Array wird das Langtelegramm zusammengebaut. Am Anfang ist die L�nge unbekannt, nach dem das erste Paket empfangen wurde, wird die L�nge
		 * gesetzt.
		 */
		private byte[] _telegram = null;

		/**
		 * Speichert die Art des Pakets, alle folgenden Telegramme m�ssen von der gleichen Art sein. (Ist das erste Paket ein StartBigBlock, dann muss der Rest von
		 * Typ NextBigPiece oder EndBigBlock sein. Der Typ der Daten ist BIG.)
		 */
		private LongTelegramType _typeOfData;

		/** Speichert wieviele St�cke insgesamt zu einem Lang-Telegramm geh�ren (dieser Wert wird nur bei Big und Bigger Paketen benutzt) */
		private int _numberOfNeededPieces;

		/** Speichert wieviele Pakete bisher empfangen wurden (daraus ergibt sich auch die Nummer des n�chsten St�cks, das erwartet wird) */
		private int _numberOfPiecesReceived = 0;

		/** Bestimmt, an welcher Stelle das empfangende Byte-Array eingef�gt werden soll */
		private int _currentByteArrayPosition = 0;

		private boolean _firstTelegramReceived = false;

		/** Diese Variable wird true, sobald ein Telegramm fertiggestellt ist. */
		private boolean _telegramFinished = false;

		public Receiver(ReceiverKey keyForThisReceiver, Osi7LongTelegramRecombine receiverManager) {
			_keyForThisReceiver = keyForThisReceiver;
			_receiverManager = receiverManager;
		}

		public void newData(byte[] receivedData) {

			if(!_firstTelegramReceived) {

				// Es wurde noch kein erstes Paket empfangen, also wird das gerade �bergebene Paket das erste sein.
				// Dies wird gepr�ft.

				try {
					analyzeFirstTelegram(receivedData);
				}
				catch(Exception e) {
					_debug.warning("Fehler bei der Auswertung des ersten Pakets", e);
					// Es gab ein Fehler bei der Auswertung des ersten Pakets, somit
					// k�nnen die restlichen Pakete ebenfalls verworfen werden.
					// Falls weitere Pakete f�r diesen Empf�nger ankommen (die NextBigPiece, NextBiggerPiece, usw)
					// werden diese automatisch verworfen, da sie ebenfalls die Pr�fung nicht �berstehen und
					// hier "landen".
					cancelReceiver();
				}
			}
			else {
				// Das Startpaket wurde empfangen, es kann aber passieren, dass ein zweites Startpaket empfangen wird.
				// Beispiel: Der Sender hat ein Problem und ist nicht mehr in der Lage Telegramme zu verschicken.
				// Die ersten Telegramme werden wieder Starttelegramme sein.

				if(isFirstTelegram(receivedData)) {
					// Es ist ein neues Startpaket, also alle alten Daten l�schen
					handleSecondStartTelegram();
					// Methode erneut aufrufen, die alten Daten wurden gel�scht, das "neue" Startpaket wird erkannt und
					// alle beginnt von vorne
					newData(receivedData);
				}
			}

			// Zu diesem Zeitpunkt steht die Gr��e des Empfangsarrays fest, der Typ des Telegramms (Short, Big, Bigger)
			// steht ebenfalls fest. Also k�nnen die Daten aus dem empfangenen Telegramms in das Byte-Array kopiert
			// werden.

			final LongTelegramType typeOfCurrentTelegram = Osi7LongTelegramRecombine.getLongType(receivedData);

			if(Osi7LongTelegramRecombine.getLongType(receivedData) == LongTelegramType.SHORT_BLOCK) {
				// Es ist ein ShortBlock, es gibt also nur ein Telegramm
				System.arraycopy(receivedData, 6, _telegram, _currentByteArrayPosition, _telegram.length);
				telegramCompleted();
			}
			else if((_typeOfData == LongTelegramType.BIG) && (typeOfCurrentTelegram == LongTelegramType.START_BIG_BLOCK
			                                                  || typeOfCurrentTelegram == LongTelegramType.NEXT_BIG_PIECE
			                                                  || typeOfCurrentTelegram == LongTelegramType.END_BIG_BLOCK)) {
				// Das gesamte Telegramm ist ein Big-Telegramm, das gerade empfangende St�ck ist entweder der Start, ein Mittelst�ck oder das Endst�ck

				if(typeOfCurrentTelegram == LongTelegramType.NEXT_BIG_PIECE) {

					// Wurde dieses Mittelst�ck erwartet ?
					final int numberOfDataPiece = (receivedData[6] & 0xFF);
					if(numberOfDataPiece != _numberOfPiecesReceived + 1) {
						// Es wurde nicht der Nachfolger empfangen, also kann alles verworfen werden
						cancelReceiver();
						_debug.warning(
								"Ein BigNextPiece wird verworfen, weil ein BigPiece mit der Nummer " + (_numberOfPiecesReceived + 1)
								+ " erwartet wurde, empfangen wurde " + numberOfDataPiece
						);
						// Es macht keinen Sinn weiter zu machen
						return;
					}

					// Es ist ein Mittelst�ck, in diesem Fall steht der Teil des Byte-Arrays ab Index 7
					// Die L�nge des �bergebenen Byte-Arrays ergibt sich auf Gesamtel�nge - Steuerinformationen

					final int lengthOfData = receivedData.length - 7;
					System.arraycopy(receivedData, 7, _telegram, _currentByteArrayPosition, lengthOfData);

					// Das Byte-Array wurde kopiert, also muss beim n�chsten Byte-Array ein anderer Startindix
					// benutzt werden.
					_currentByteArrayPosition = _currentByteArrayPosition + lengthOfData;
					// Es wurde ein St�ck empfangen
					_numberOfPiecesReceived++;
				}
				else if(typeOfCurrentTelegram == LongTelegramType.START_BIG_BLOCK) {
					// Das Startst�ck wurde empfangen, die Daten stehen ab Index

					final int lengthOfData = receivedData.length - 9;
					System.arraycopy(receivedData, 9, _telegram, _currentByteArrayPosition, lengthOfData);

					// Das Byte-Array wurde kopiert, also muss beim n�chsten Byte-Array ein anderer Startindix
					// benutzt werden.
					_currentByteArrayPosition = _currentByteArrayPosition + lengthOfData;
					// Es wurde ein St�ck empfangen

				}
				else if(typeOfCurrentTelegram == LongTelegramType.END_BIG_BLOCK) {
					// Das Endst�ck wurde empfangen, es muss kein Byte-Array vorhanden sein !

					if(receivedData.length > 8) {
						// Es gibt 8 Bytes Steuerinformationen, also sind Daten vorhanden

						final int lengthOfData = receivedData.length - 8;

						System.out.println("*****************************************");
						System.out.println("Endtelegramml�nge = " + _telegram.length);
						System.out.println("_currentByteArrayPosition = " + _currentByteArrayPosition);
						System.out.println("lengthOfData = " + lengthOfData);
						System.out.println("recieved length = " + receivedData.length);
						System.out.println("*****************************************");
						System.out.println("");
						System.arraycopy(receivedData, 6, _telegram, _currentByteArrayPosition, lengthOfData);
					}

					// Wurden alle Datenst�cke empfangen ?
					if(_numberOfNeededPieces != _numberOfPiecesReceived) {
						// Das letzte Telegramm wurde zwar empfangen, aber es fehlt ein St�ck oder es sind
						// zuviele St�cke empfangen worden. Auf alle F�lle wird das Telegramm verworfen
						_debug.warning(
								"Ein Big-Telegramm sollte beendet werden, es wurden " + _numberOfNeededPieces + " erwartet und " + _numberOfPiecesReceived
								+ " empfangen. Das Telegramm wird verworfen."
						);
						cancelReceiver();
					}
					else {
						// Alle St�cke wurden empfangen, das Telegramm ist fertig
						telegramCompleted();
					}
				}
				else {
					// Das Telegramm geh�rt nicht zu einem Big Telegramm, Fehler ausgeben und abwarten was passiert
					_debug.error(
							"Ein Big-Telegramm hat ein Telegramm empfangen, mit dem es nichts anfangen kann. Typ des Telegramms: " + typeOfCurrentTelegram
							+ " Das Big-Telegramm wird nicht verworfen, es wird auf weitere Pakete gewartet"
					);
				}
			}
			else if((_typeOfData == LongTelegramType.BIGGER) && (typeOfCurrentTelegram == LongTelegramType.START_BIGGER_BLOCK
			                                                     || typeOfCurrentTelegram == LongTelegramType.NEXT_BIGGER_PIECE
			                                                     || typeOfCurrentTelegram == LongTelegramType.END_BIGGER_BLOCK)) {

				if(typeOfCurrentTelegram == LongTelegramType.NEXT_BIGGER_PIECE) {

					// Wurde dieses Mittelst�ck erwartet ?
					int numberOfDataPiece = (receivedData[6] & 0xFF);
					numberOfDataPiece = numberOfDataPiece << 8;
					numberOfDataPiece = (numberOfDataPiece | (receivedData[7] & 0xFF));

					if(numberOfDataPiece != _numberOfPiecesReceived + 1) {
						// Es wurde nicht der Nachfolger empfangen, also kann alles verworfen werden
						cancelReceiver();
						_debug.warning(
								"Ein BiggerNextPiece wird verworfen, weil ein BiggerPiece mit der Nummer " + (_numberOfPiecesReceived + 1)
								+ " erwartet wurde, empfangen wurde " + numberOfDataPiece
						);
						// Es macht keinen Sinn weiter zu machen
						return;
					}

					// Es ist ein Mittelst�ck, in diesem Fall steht der Teil des Byte-Arrays ab Index 8
					// Die L�nge des �bergebenen Byte-Arrays ergibt sich auf Gesamtel�nge - Steuerinformationen

					final int lengthOfData = receivedData.length - 8;
					System.arraycopy(receivedData, 8, _telegram, _currentByteArrayPosition, lengthOfData);

					// Das Byte-Array wurde kopiert, also muss beim n�chsten Byte-Array ein anderer Startindix
					// benutzt werden.
					_currentByteArrayPosition = _currentByteArrayPosition + lengthOfData;
					// Es wurde ein St�ck empfangen
					_numberOfPiecesReceived++;
				}
				else if(typeOfCurrentTelegram == LongTelegramType.START_BIGGER_BLOCK) {
					// Das Startst�ck wurde empfangen, die Daten stehen ab Index 11

					final int lengthOfData = receivedData.length - 11;
					System.arraycopy(receivedData, 11, _telegram, _currentByteArrayPosition, lengthOfData);

					// Das Byte-Array wurde kopiert, also muss beim n�chsten Byte-Array ein anderer Startindix
					// benutzt werden.
					_currentByteArrayPosition = _currentByteArrayPosition + lengthOfData;
					// Es wurde ein St�ck empfangen
					// _numberOfPiecesReceived++;

				}
				else if(typeOfCurrentTelegram == LongTelegramType.END_BIGGER_BLOCK) {
					// Das Endst�ck wurde empfangen, es muss kein Byte-Array vorhanden sein !

					if(receivedData.length > 10) {
						// Es gibt 10 Bytes Steuerinformationen, also sind Daten vorhanden
						final int lengthOfData = receivedData.length - 10;
						System.arraycopy(receivedData, 6, _telegram, _currentByteArrayPosition, lengthOfData);
						// _numberOfPiecesReceived++;
					}

					// Wurden alle Datenst�cke empfangen ?
					if(_numberOfNeededPieces != _numberOfPiecesReceived) {
						// Das letzte Telegramm wurde zwar empfangen, aber es fehlt ein St�ck oder es sind
						// zuviele St�cke empfangen worden. Auf alle F�lle wird das Telegramm verworfen
						_debug.warning(
								"Ein Bigger-Telegramm sollte beendet werden, es wurden " + _numberOfNeededPieces + " erwartet und " + _numberOfPiecesReceived
								+ " wirklich empfangen. Das Telegramm wird verworfen."
						);
						cancelReceiver();
					}
					else {
						// Es wurden alle Telegrammst�cke empfangen
						telegramCompleted();
					}
				}
				else {
					// Das Telegramm geh�rt nicht zu einem Bigger Telegramm, Fehler ausgeben und abwarten was passiert
					_debug.error(
							"Ein Bigger-Telegramm hat ein Telegramm empfangen, mit dem es nichts anfangen kann. Typ des Telegramms: " + typeOfCurrentTelegram
							+ " Das Big-Telegramm wird nicht verworfen, es wird auf weitere Pakete gewartet"
					);
				}
			}
		}

		/** Diese Methode wird aufgerufen, wenn ein Telegramm komplett empfangen wurde. */
		private void telegramCompleted() {
			// System.out.println(toString());
			_telegramFinished = true;
		}

		/** @return true = Das Langtelegramm wurde rekombiniert und kann weitergereicht werden; false = Das Langtelegramm wurde noch nicht fertiggestellt */
		public boolean isTelegramFinished() {
			return _telegramFinished;
		}

		/**
		 * Gibt das fertige Telegramm zur�ck und entfernt den Empf�nger aus der Liste von Empf�ngern.
		 *
		 * @return fertiges Langtelegramm
		 *
		 * @throws IllegalStateException Das Langtelegramm wurde noch nicht fertiggestellt
		 */
		public byte[] getLongTelegram() throws IllegalStateException {
			if(isTelegramFinished()) {
				// Das Telegramm wurde fertiggestellt und wird angefordert. Somit k�nnen die Daten f�r diesen Empf�nger
				// freigegben werden. Der Schl�ssel wird aus der HashMap entfernt, falls neue Daten kommen, werden f�r diese
				// ein neues Objekt angelegt !
				_receiverManager.removeReceiver(_keyForThisReceiver);
				return _telegram;
			}
			else {
				throw new IllegalStateException(
						"Das angeforderte Langtelegramm wurde noch nicht fertiggestellt und kann nicht angefordert werden. Art des Langtelegramms: "
						+ _typeOfData + " Empf�ngerschl�ssel: " + _keyForThisReceiver
				);
			}
		}

		public String toString() {
			StringBuffer string = new StringBuffer();
			string.append(
					"*********************************\nEin Telegramm wurde zusammengesetzt:\nGesamtl�nge der Nutzdaten: " + _telegram.length
					+ "\nTelegrammtyp: " + _typeOfData + "\nAnzahl Pieces: " + _numberOfNeededPieces + " Anzahl Pieces empfangen: " + _numberOfPiecesReceived
					+ "\nSenderknotennummer: " + _keyForThisReceiver.getSenderAddress()
			);

			// Daten hinzuf�gen
			string.append("\n�bertragene Nutzdaten:\n");

			for(int nr = 0; nr < _telegram.length; nr++) {
				string.append(_telegram[nr] + " ");
				if((nr % 50 == 0) && (nr != 0)) string.append("\n");
			}

			string.append("\n*********************************");
			return string.toString();
		}

		/**
		 * Diese Methode wird aufgerufen, wenn ein Receiverobjekt sich aus der Liste der Empf�nger austragen lassen m�chte. Ein Grund w�re, das Telegramm wurde
		 * komplett empfangen oder ein Fehler ist aufgetreten.
		 */
		private void cancelReceiver() {
			_receiverManager.removeReceiver(_keyForThisReceiver);
		}

		/**
		 * Diese Methode wird aufgerufen, wenn ein Teiltelegramm empfangen wurde, das ein neues Startpaket ist. Es k�nnte sich bei diesem Paket um ein ShortBlock,
		 * StartBig/Bigger handeln. Ist das der Fall, m�ssen die alten Daten verworfen werden. Aber das gerade empfangene Telegramm kann als Start f�r die folgenden
		 * Telegramme benutzt werden. Ein Beispiel w�re, der Sender kann nicht mehr senden, nach Tagen verschickt er wieder Telegramme.
		 * <p/>
		 * Die alten Daten werden verworfen.
		 */
		private void handleSecondStartTelegram() {
			// Die alten Daten k�nnen verworfen werden, dies ist n�tig wenn das Telegramm wirklich ein Starttelegramm ist,
			// da in diesem Fall das Telegramm einfach wieder der newData Methode �bergeben wird und f�r die Methode
			// erkennt nicht, das sie das Telegramm zum zweiten mal bekommt.
			_telegram = null;
			_currentByteArrayPosition = 0;
			_firstTelegramReceived = false;
			_numberOfNeededPieces = 0;
			_numberOfPiecesReceived = 0;
			_telegramFinished = false;
			_typeOfData = null;
		}

		/**
		 * Diese Methode pr�ft, ob ein Telegramm als g�ltiges Starttelegramm erkannt werden k�nnte. G�ltig bedeutet, dass das Telegramm ein ShortBlock, StartBig,
		 * StartBiggerBlock ist.
		 *
		 * @param firstTelegram Telegramm, das gepr�ft werden soll.
		 *
		 * @return
		 */
		private boolean isFirstTelegram(final byte[] firstTelegram) {

			// Alle Langtelegramme sind an dieser Stelle 0, ist das nicht der Fall, dann ist es auch kein Startpaket
			if(firstTelegram[3] != 0) return false;

			final LongTelegramType typeFirstPackage = Osi7LongTelegramRecombine.getLongType(firstTelegram);
			if((typeFirstPackage == LongTelegramType.SHORT_BLOCK) || (typeFirstPackage == LongTelegramType.START_BIG_BLOCK) || (typeFirstPackage
			                                                                                                                    == LongTelegramType
					.START_BIGGER_BLOCK)) {
				// Es ist ein Startpaket
				return true;
			}
			else {
				// Es ist ein anderer Pakettyp, der kein Startpaket darstellt
				return false;
			}
		}

		/**
		 * Diese Methode pr�ft ein Paket, ob es ein g�ltiges Startpaket ist (ShortBlock, StartBigBlock, StartBiggerBlock). Falls es kein g�ltiges Startpaket ist, wird
		 * ein IllegalStateException geworfen. Dies f�hrt dazu, dass der Empf�mger sich aus der Map l�scht und keine weiteren Pakete empfangen kann. Folgepakete des
		 * ersten Fehlerhaften Pakets werden ebenfalls hier gepr�ft und verworfen.
		 *
		 * @param firstTelegram Telegramm, das gepr�ft werden soll
		 *
		 * @throws IllegalStateException Das �bergebene Telegramm ist kein g�ltiges erstes Paket (ShortBlock, StartBigBlock, StartBiggerBlock)
		 */
		private void analyzeFirstTelegram(final byte[] firstTelegram) throws IllegalStateException {
			// Um was f�r einen Startpakettyp handelt es sich ? (Short, Big, Bigger)

			if(firstTelegram[3] != 0) throw new IllegalArgumentException("Langtelegramm-Kennung ist ungleich 0");

			final LongTelegramType typeFirstPackage = Osi7LongTelegramRecombine.getLongType(firstTelegram);
			if(typeFirstPackage == LongTelegramType.SHORT_BLOCK) {
				_typeOfData = LongTelegramType.SHORT_BLOCK;
				// Wie gro� muss das Byte-Array sein, um die Daten aufzunehmen
				// (die ersten 6 Bytes enthalten Steuerungsinformationen)
				final int sizeOfData = firstTelegram.length - 6;
				_telegram = new byte[sizeOfData];
			}
			else if(typeFirstPackage == LongTelegramType.START_BIG_BLOCK) {
				_typeOfData = LongTelegramType.BIG;
				int sizeOfData = 0;
				sizeOfData = (sizeOfData | (firstTelegram[6] & 0xFF));
				sizeOfData = sizeOfData << 8;
				sizeOfData = (sizeOfData | (firstTelegram[7] & 0xFF));

				_telegram = new byte[sizeOfData];
				_numberOfNeededPieces = firstTelegram[8];
			}
			else if(typeFirstPackage == LongTelegramType.START_BIGGER_BLOCK) {
				_typeOfData = LongTelegramType.BIGGER;
				int sizeOfData = 0;
				sizeOfData = (sizeOfData | (firstTelegram[6] & 0xFF));
				sizeOfData = sizeOfData << 8;
				sizeOfData = (sizeOfData | (firstTelegram[7] & 0xFF));
				sizeOfData = sizeOfData << 8;
				sizeOfData = (sizeOfData | (firstTelegram[8] & 0xFF));

				_telegram = new byte[sizeOfData];

				int neededPiecesHelper = 0;
				neededPiecesHelper = (neededPiecesHelper | (firstTelegram[9] & 0xFF));
				neededPiecesHelper = neededPiecesHelper << 8;
				neededPiecesHelper = (neededPiecesHelper | (firstTelegram[10] & 0xFF));
				_numberOfNeededPieces = neededPiecesHelper;
			}
			else {
				// Es ist ein anderer Pakettyp, der kein Startpaket darstellt
				throw new IllegalStateException(
						"Das erste Telegramm, das zu einem Empf�nger geschickt wurde ist kein Startpaket: " + typeFirstPackage
				);
			}

			// Das erste Telegramm wurde analysiert
			_firstTelegramReceived = true;
		}
	}

	private final static class ReceiverKey {

		final int _senderAddress;

		final int _dataBlockType;

		final int _numberOfDataBlock;

		/**
		 * Dieser Schl�ssel wird ben�tigt um den Empf�nger eines Datenpakets zu identifizieren. Das gesamte Datenpaket wird �bergeben, die ben�tigten Informationen
		 * werden automatisch ausgelesen.
		 *
		 * @param receivedData Datenpaket, das empfangen wurde
		 */
		public ReceiverKey(byte[] receivedData) {

			_senderAddress = Osi7LongTelegramRecombine.getSenderAddress(receivedData);

			// Die ersten 4 Bytes auf 0 und dann nach rechts shiften
			_dataBlockType = Osi7LongTelegramRecombine.getDataBlockType(receivedData);

			// Die letzten 4 Bytes auf 0
			_numberOfDataBlock = Osi7LongTelegramRecombine.getDataBlockNumber(receivedData);
		}

		public int hashCode() {
			int hashCode = 17;

			// BlockType konstant 1
			hashCode = hashCode + _dataBlockType;
			// numberOfDataBlock konstante, 0-15
			hashCode = hashCode * 37 + ((_numberOfDataBlock + 1) * 87);
			// beliebige positive Zahl
			hashCode = hashCode * 41 + _senderAddress * 13;

			return (int)hashCode;
		}

		public boolean equals(Object o) {
			if(!(o instanceof ReceiverKey)) {
				// Falsches Objekt
				return false;
			}

			final ReceiverKey keyObject = (ReceiverKey)o;

			if(_dataBlockType != keyObject.getDataBlockType()) {
				return false;
			}

			if(_numberOfDataBlock != keyObject.getNumberOfDataBlock()) {
				return false;
			}

			if(_senderAddress != keyObject.getSenderAddress()) {
				return false;
			}

			return true;
		}

		public int getSenderAddress() {
			return _senderAddress;
		}

		public int getDataBlockType() {
			return _dataBlockType;
		}

		public int getNumberOfDataBlock() {
			return _numberOfDataBlock;
		}


		public String toString() {
			return "ReceiverKey{" + "_senderAddress=" + _senderAddress + ", _dataBlockType=" + _dataBlockType + ", _numberOfDataBlock=" + _numberOfDataBlock
			       + "}";
		}
	}
}
