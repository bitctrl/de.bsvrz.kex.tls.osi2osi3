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

import de.bsvrz.kex.tls.osi2osi3.osi3.DestinationUnreachableException;
import de.bsvrz.kex.tls.osi2osi3.osi3.NetworkLayer;
import de.bsvrz.sys.funclib.concurrent.Semaphore;
import de.bsvrz.sys.funclib.debug.Debug;
import de.bsvrz.sys.funclib.hexdump.HexDumper;

import java.util.*;

/**
 * Zerlegt ein Langtelegramm und verschickt die Teilst�cke
 *
 * @author Kappich Systemberatung
 * @version $Revision: 5114 $
 */
public class Osi7LongTelegramSegment {

	/** Knotennummer des Senders, dieser Sender verschickt alle Langtelegramme */
	private final int _nodeNumberSender;

	/** DebugLogger f�r Debug-Ausgaben */
	private static final Debug _debug = Debug.getLogger();


	/** Speichert Sender/Empf�ngerkombinationen */
	private final Map<Integer, SenderReceiverCombination> _sender = new HashMap<Integer, SenderReceiverCombination>();

	/** Objekt, das nacheinander Telegramm verschickt */
	private final SenderThread _senderThread;

	/**
	 * @param nodeNumberSender Knotennummer des Sender
	 * @param sender           Objekt, mit dem Telegramme verschickt werden k�nnen
	 */
	public Osi7LongTelegramSegment(int nodeNumberSender, NetworkLayer sender) {
		_nodeNumberSender = nodeNumberSender;
		_senderThread = new SenderThread(_sender, sender);

		final Thread thread = new Thread(_senderThread);
		thread.start();
	}

	/**
	 * Diese Methode zerlegt ein Langtelegramm und verschickt es an den entsprechenden Empf�nger. Die Methode kann blockieren, falls eine bestimmte Anzahl von
	 * Telegrammen von diesem Sender zu dem angegebenen Empf�nger unterwegs sind.
	 *
	 * @param nodeNumberReceiver Knotennummer des Empf�ngers
	 * @param longTelegram       Langtelegramm, das verschickt werden soll
	 * @param priority           Priorit�t, mit der das Telegramm verschickt werden soll
	 */
	public void sendLongData(final int nodeNumberReceiver, final byte[] longTelegram, final int priority) {
		// In der Map nach der Sender/Empf�ngerkombination suchen und dieser das Telegramm �bergeben.
		// Falls das Objekt noch nicht exisitert, wird es angelegt.

		final SenderReceiverCombination combination;
		synchronized(_sender) {
			if(_sender.containsKey(nodeNumberReceiver) == false) {
				// Es gibt diese Kombination noch nicht
				final SenderReceiverCombination senderReceiverCombination = new SenderReceiverCombination(_nodeNumberSender, _senderThread, nodeNumberReceiver);
				_sender.put(nodeNumberReceiver, senderReceiverCombination);
			}

			// Dieses Objekt nimmt das Telegramm entgegen und verschickt es
			combination = _sender.get(nodeNumberReceiver);
		}
		// Dieser Aufruf kann blockieren, wenn bereits 15 Telegramme zu diesem Empf�nger unterwegs sind
		combination.sendLongTelegram(longTelegram, priority);
	}

	/**
	 * Diese Klasse speichert eine Sender/Empf�ngerkombination und verwaltet diese. Es d�rfen nur eine bestimmte Anzahl von Telegramme f�r eine Kombination
	 * verschickt werden. Wenn ein Telegramm verschickt wurde, darf ein weiteres Paket verschickt werden, usw. .
	 */
	private final static class SenderReceiverCombination {

		/** Wird in jedem Telegramm mitgeschickt */
		final int _nodeNumberSender;

		/** Telegramme, die gleichzeitig verschickt werden d�rfen */
		final int _numberOfParallelSendTelegrams = 15;

		/** Wird jedem Telegrammobjekt mitgegeben und wird ben�tigt um an dieses Objekt �ber eine Map zu kommen */
		final int _nodeNumberReceiver;

		/**
		 * Bei jedem Telegramm, das gesendet werden soll, muss ein Lock angefordert werden. Ist die Anzahl Locks aufgebraucht, ist der Versuch ein weiteres Telegramm
		 * zu verschicken blockierend.
		 */
		final Semaphore _parallelLocks = new Semaphore(_numberOfParallelSendTelegrams);

		/**
		 * Diese Liste speichert alle Datenblocknummer, die noch benutzt werden d�rfen. Wird eine Nummer ben�tigt, wird diese aus der Liste entfernt. Wird einer
		 * Nummer "frei" wird diese hinten an der Liste angef�gt.
		 */
		private final LinkedList<Byte> _listWithUnusedDataBlockNumbers = new LinkedList<Byte>();

		/** Verschickt das Telegramm */
		private final SenderThread _sender;

		public SenderReceiverCombination(int nodeNumberSender, SenderThread sender, int nodeNumberReceiver) {
			_nodeNumberSender = nodeNumberSender;
			_sender = sender;
			_nodeNumberReceiver = nodeNumberReceiver;

			// Nummern erzeugen, die als Datenblocknummern benutzt werden
			for(int nr = 1; nr < 16; nr++) {
				_listWithUnusedDataBlockNumbers.add((byte)nr);
			}
		}

		/**
		 * Methode, die ein Lang-Telegramm verschickt. Das Langtelegramm wird in eine Queue eingetragen und abgearbeitet, sobald es an der Reihe ist
		 *
		 * @param longTelegram Lang-Telegramm, das verschickt werden soll
		 */
		public void sendLongTelegram(byte[] longTelegram, int priority) {
			_parallelLocks.acquire();

			// Eine Datenblocknummer anfordern
			final byte dataBlockNumber;
			synchronized(_listWithUnusedDataBlockNumbers) {
				try {
					dataBlockNumber = _listWithUnusedDataBlockNumbers.removeFirst();
				}
				catch(Exception e) {
					e.printStackTrace();
					_debug.error("Es m��te eine Datenblocknummer verf�gbar sein", e);
					return;
				}
			}

			final FragmentedLongTelegram fragmentedLongTelegram = new FragmentedLongTelegram(
					longTelegram, _nodeNumberSender, dataBlockNumber, _nodeNumberReceiver, priority
			);

			_sender.sendTelegram(fragmentedLongTelegram);
		}

		/**
		 * Diese Methode gibt alle Ressourcen eines Telegramms wieder frei, da es versendet wurde. Es wird die DatenBlockNummer und ein Semaphore freigegeben.
		 *
		 * @param removeTelegram Telegramm, das verschickt wurde
		 */
		public void removeTelegram(FragmentedLongTelegram removeTelegram) {
			_debug.finest("DatenBlocknummer wird freigegeben: " + removeTelegram.getDataBlockNumber());
			synchronized(_listWithUnusedDataBlockNumbers) {
				// Die Nummer kann wieder verwendet werden (die Nummer wird hinten angef�gt, damit
				// sie "m�glichst sp�t" wieder benutzt wird. somit werden alle Nummern "reihe um" benutzt)
				_listWithUnusedDataBlockNumbers.addLast(removeTelegram.getDataBlockNumber());
				// Es darf wieder ein Telegramm an diesen Empf�nger verschickt werden, da eine freie DatenBlockNummer
				// vorhanden ist
				_parallelLocks.release();
			}
		}
	}

	/** Verschickt Langtelegramme */
	private final static class SenderThread implements Runnable {

		/** Liste, die alle sendebereiten Telegramme enth�lt */
		private final List<FragmentedLongTelegram> _telegrams = new LinkedList<FragmentedLongTelegram>();

		/**
		 * Speichert alle "Sender/Empf�nger Kombinationen. Mit dem Objekt ist es m�glich ein versendetes Telegramm zu melden, somit kann das n�chste Telegramm an den
		 * Empf�nger verschickt werden (falls eines vorhanden ist)
		 */
		private final Map<Integer, SenderReceiverCombination> _senderInformations;

		private final NetworkLayer _senderObject;

		public SenderThread(Map<Integer, SenderReceiverCombination> senderInformations, NetworkLayer senderObject) {
			Thread.currentThread().setName("LongTelegramSender");
			_senderInformations = senderInformations;
			_senderObject = senderObject;
		}

		public void sendTelegram(FragmentedLongTelegram newTelegram) {
			synchronized(_telegrams) {
				_telegrams.add(newTelegram);
				// Falls jemand auf neue Telegramme wartet
				_telegrams.notifyAll();
			}
		}


		public void run() {

			while(Thread.currentThread().isInterrupted() == false) {
				synchronized(_telegrams) {
					while(_telegrams.size() == 0) {
						// Es gibt keine Telegramme, die verschickt werden k�nnen
						try {
							_telegrams.wait();
						}
						catch(InterruptedException e) {
							_debug.warning("Thread zum verschicken von Langtelegrammen wird beendet", e);
						}
					}

					// Es gibt mindestens ein Telegramm, das verschickt werden soll

					// Diese Liste speichert alle Telegramme, die entfernt werden k�nnen
					// (weil entweder alle Daten verschickt wurden oder weil das Telegramm aufgrund eines Fehlers einen Teil nicht �bertragen konnte)
					final List<FragmentedLongTelegram> removeTelegramList = new LinkedList<FragmentedLongTelegram>();

					for(int nr = 0; nr < _telegrams.size(); nr++) {
						FragmentedLongTelegram fragmentedLongTelegram = _telegrams.get(nr);

						// Teilst�ck holen und verschicken
						final byte[] telegramPiece = fragmentedLongTelegram.getNextPiece();

						// Diese Variable zeigt an, ob ein Teiltelegramm �bertragen werden konnte
						// true = Fehler, Telegramm komplett l�schen
						boolean transferError = false;

						try {
							sendTelegramPiece(fragmentedLongTelegram.getSenderNodeNumber(), fragmentedLongTelegram.getPriority(), telegramPiece);
						}
						catch(DestinationUnreachableException e) {
							// Das Telegramm kann nicht verschickt werden, also k�nnen die restlichen Daten gel�scht werden.
							// Dieser Schritt ist notwendig, da nicht bekannt ist, wann die restlichen Daten verschickt werden k�nnen und
							// es nicht erw�nscht ist, dass die Daten �ber Tage gepuffert werden.
							transferError = true;
							_debug.error(
									"Langtelegramm kann nicht versendet werden: Knotennummer Sender: " + fragmentedLongTelegram.getSenderNodeNumber()
									+ " Priorit�t: " + fragmentedLongTelegram.getPriority() + " Langtelegramm(st�ck): " + HexDumper.toString(telegramPiece), e
							);
						}

						if((fragmentedLongTelegram.isOnePieceLeft() == false) || (transferError)) {
							// Das Telegramm hat alle Teilst�cke verschickt, somit kann es gel�scht werden oder
							// es ist ein Fehler aufgetreten
							removeTelegramList.add(fragmentedLongTelegram);
						}
					}

					// Die Liste mit den Telegrammen, die senden k�nnen, um die Telegramme bereinigen, die alle
					// Daten verschickt haben oder die nicht �bertragen werden konnten (Fehler beim verschicken eines Teiltelegramms)

					for(int nr = 0; nr < removeTelegramList.size(); nr++) {
						FragmentedLongTelegram telegramDelete = removeTelegramList.get(nr);

						_telegrams.remove(telegramDelete);

						// F�r die Sender/Empf�ngerkombination wird die Nummer und der Semaphore freigegben.
						// Somit kann ein weiteres Telegramm losgeschickt werden.
						synchronized(_senderInformations) {
							_senderInformations.get(telegramDelete.getReceiverNodeNumber()).removeTelegram(telegramDelete);
						}
					}
				}
			}
		}

		private void sendTelegramPiece(int senderNodeNumber, int priority, byte[] pieceOfTelegram) throws DestinationUnreachableException {
			_senderObject.sendData(senderNodeNumber, priority, pieceOfTelegram, false);
		}
	}

	/** Zerlegt ein Langtelegramm in Teilst�cke und stellt diese per Methodenaufruf zur Verf�gung */
	private final static class FragmentedLongTelegram {

		/** Speichert das zu �bertragene Telegramm */
		private final byte[] _wholeTelegram;

		/** Adresse des Senders */
		private final int _senderNodeNumber;

		/** Die Empf�ngeradresse wird von einer anderen Klasse ben�tigt, um an den Empf�nger zu kommen. Der Wert dient als Key in einer Map. */
		private final int _receiverNodeNumber;

		/** Speichert den Long-Type, den das zu �bertragenen Telegramm haben muss damit alle Daten �bertragen werden k�nnen */
		private final LongTelegramType _telegramType;

		/** Speichert den Index im Byte-Array, ab dem Daten verschickt werden d�rfen. Diese "Marke" wandert �ber das Byte-Array, bis alle Daten verschickt wurden. */
		private int _dataPosition = 0;

		/** Speichert, wieviele Bytes noch verschickt werden m�ssen. Ist diese Zahl 0, wurden alle Byte verschickt. */
		private int _dataBytesLeft = 0;

		/** Gibt an, ob noch ein Teil-Telegramm vorhanden ist, das verschickt werden muss. */
		private boolean _onePieceLeft = true;

		/** Gibt an, ob der StartBig/StartBigger Block schon verschickt wurde */
		private boolean _startBlockSend = false;

		/**
		 * Legt die maximale Gr��e eines Teil-Telegramms fest. Dieser Platz steht f�r Header, Nutzdaten, CRC zur Verf�gung. So bleibt gen�gend Platz f�r das OSI 3
		 * Routing.
		 */
		private final int _maxDataForTelegram = 238;

		/**
		 * Speichert die maximale Anzahl von Bytes, die in einem LongTelegram vom Typ Big gesendet werden kann. Anzahl NextBigPiece * Bytes pro BigPiece (253*231) +
		 * Bytes, die in einem StartBig verpackt werden k�nnen (229) + Bytes, die in einem EndBig verpackt werden k�nnen (230)
		 */
		private final int _maxDataForBigTelegram = (231 * 253) + 229 + 230;

		/**
		 * Jeder DatenBlock, der verschickt wird, bekommt die gleiche Nummer, aus dieser Nummer schlie�t der Empf�nger zu welchem Langtelegramm das Teilst�ck geh�rt.
		 * (Die Zahl ist zwischen 1 und 15)
		 */
		private final byte _dataBlockNumber;

		/** Wieviele Datenblockst�cke m�ssen verschickt werden (Start und Ende z�hlen nicht) */
		private final int _numberOfNeededPieces;

		/** Nummer des Telegramms, das verschickt werden soll. 0 = StartBlock . . . _numberOfNeededPieces + 1 = EndBlock */
		private int _telegramNumber = -1;

		/** Speichert die CRC-Pr�fsumme TBD in dieser Version immer 0 */
		private final int _crcChecksum = 0;

		/** Wie viele Bytes "kosten" die Steuerungsinformationen eines StartBiggerBlocks */
		private final int _costStartBiggerBlock = 11;

		/** Wie viele Bytes "kosten" die Steuerungsinformationen eines nextBiggerPiece */
		private final int _costNextBiggerPiece = 8;

		/** Wie viele Bytes "kosten" die Steuerungsinformationen eines nextBiggerPiece */
		private final int _costEndBiggerBlock = 10;

		/** Priorit�t, mit der die Telegrammst�cke verschickt werden sollen */
		private final int _priority;

		public FragmentedLongTelegram(byte[] telegram, int senderNodeNumber, byte dataBlockNumber, int receiverNodeNumber, int priority) {
			_wholeTelegram = telegram;
			_senderNodeNumber = senderNodeNumber;
			_dataBlockNumber = dataBlockNumber;
			_receiverNodeNumber = receiverNodeNumber;
			_priority = priority;

			_debug.finer("Es wird ein neues FragmentTelegram erzeugt: Sendernummer " + _senderNodeNumber + " Datenblocknummer " + _dataBlockNumber);

			// Es m�ssen noch alle Bytes verschickt werden
			_dataBytesLeft = _wholeTelegram.length;

			// Welcher Type (Short,Big,Bigger) muss benutzt werden, um das gesamte Telegramm zu verschicken

			if((_wholeTelegram.length + 6) <= _maxDataForTelegram) {
				// Die Daten k�nnen in einem Telegramm verschickt werden
				_telegramType = LongTelegramType.SHORT_BLOCK;

				// Bei einem ShortBlock gibt es keine Bl�cke, es wird nur ein Telegramm verschickt
				_numberOfNeededPieces = 0;
			}
			else if(_maxDataForBigTelegram > telegram.length) {
				// Es kann Big benutzt werden (253 BigPieces + BigStart + BigEnd)
				_telegramType = LongTelegramType.BIG;

				// Wieviele St�cke werden ben�tigt ?

				final int sizeOfData = _wholeTelegram.length;

				// Soviele Daten k�nnen in einem Start und Endblock verschickt werden
				final int startEndBlockDataCapazity = (_maxDataForTelegram - 9) + (_maxDataForTelegram - 8);

				// Pa�t alles in den Start und Ende Block ?

				if(sizeOfData < startEndBlockDataCapazity) {
					_numberOfNeededPieces = 0;
				}
				else {
					// Es m�ssen NextPiecesBl�cke verschickt werden, davon k�nnen Start und Endblock abgezogen werden
					// da diese auch Daten enthalten k�nnen.
					final int sizeOfNextPieceData = sizeOfData - startEndBlockDataCapazity;

					// Wieviele Datenbytes k�nnen in einem NextBigPiece verschickt werden
					final int dataForEveryPiece = _maxDataForTelegram - 7;

					if(sizeOfNextPieceData % dataForEveryPiece != 0) {
						// Es gibt einen Rest, also muss aufgerundet werden
						_numberOfNeededPieces = (sizeOfNextPieceData / dataForEveryPiece) + 1;
					}
					else {
						_numberOfNeededPieces = (sizeOfNextPieceData / dataForEveryPiece);
					}
				}
			}
			else {
				_telegramType = LongTelegramType.BIGGER;
				// Wieviele Bigger-St�cke werden ben�tigt ?
				final int sizeOfData = _wholeTelegram.length;

				// Soviele Daten k�nnen in einem Start und Endblock verschickt werden
				final int startEndBlockDataCapazity = (_maxDataForTelegram - 11) + (_maxDataForTelegram - 10);

				// Pa�t alles in den Start und Ende Block ?

				if(sizeOfData < startEndBlockDataCapazity) {
					_numberOfNeededPieces = 0;
				}
				else {
					// Es m�ssen NextBiggerPiecesBl�cke verschickt werden, davon k�nnen Start und Endblock abgezogen werden
					// da diese auch Daten enthalten k�nnen.
					final int sizeOfNextPieceData = sizeOfData - startEndBlockDataCapazity;

					// Wieviele Datenbytes k�nnen in einem NextBiggerPiece verschickt werden
					final int dataForEveryPiece = _maxDataForTelegram - 8;

					if(sizeOfNextPieceData % dataForEveryPiece != 0) {
						// Es gibt einen Rest, also muss aufgerundet werden
						_numberOfNeededPieces = (sizeOfNextPieceData / dataForEveryPiece) + 1;
					}
					else {
						_numberOfNeededPieces = (sizeOfNextPieceData / dataForEveryPiece);
					}
				}
			}
		}

		public byte[] getNextPiece() throws IllegalStateException {
			synchronized(this) {
				if(_onePieceLeft == false) {
					// Es gibt kein Teil-Telegramm mehr
					throw new IllegalStateException("Es ist kein Teil-Telegramm mehr vorhanden, Telegrammtyp: " + _telegramType);
				}

				// Was f�r ein Teil-Telegramm muss verschickt werden ?

				if(_telegramType == LongTelegramType.SHORT_BLOCK) {
					// 6 Byte Header, der Rest ist f�r Nutzdaten
					final byte[] shortTelegramm = new byte[6 + _dataBytesLeft];

					// Im �bergebenen Byte-Array wird der Header eingetragen, der R�ckgabewert bestimmt den Index,
					// ab dem Daten eingetragen werden d�rfen
					int dataIndex = createTelegramFrame(shortTelegramm);

					// Die Daten in den ShortBlock kopieren
					System.arraycopy(_wholeTelegram, _dataPosition, shortTelegramm, dataIndex, _wholeTelegram.length);

					// Es gibt kein n�chstes Telegramm mehr
					_onePieceLeft = false;

					// Alle Bytes wurden verschickt
					_dataBytesLeft = 0;

					return shortTelegramm;
				}
				else if(_telegramType == LongTelegramType.BIG) {
					// Es kann 3 F�lle geben, das erste Telegramm soll verschickt werden, ein NextPiece soll verschickt werden oder
					// es wird das Endpaket verschickt.

					_telegramNumber++;

					if(_startBlockSend == false) {

						// Der BigStartBlock wurde noch nicht verschickt. Es werden soviele Daten wie m�glich
						// verschickt.

						if(_dataBytesLeft > (_maxDataForTelegram - 9)) {
							// Es gehen nicht alle Daten in den Startblock (Normalfall)
							final byte[] startBigTelegram = new byte[_maxDataForTelegram];
							final int dataIndex = createTelegramFrame(startBigTelegram);

							System.arraycopy(_wholeTelegram, _dataPosition, startBigTelegram, dataIndex, (_maxDataForTelegram - 9));

							_dataPosition = _dataPosition + (_maxDataForTelegram - 9);

							// Es wurden _maxDataForTelegram - 9 viele Byte �bertragen
							_dataBytesLeft = _dataBytesLeft - (_maxDataForTelegram - 9);

							// Das erste Telegramm wurde verschickt
							_startBlockSend = true;

							return startBigTelegram;
						}
						else {
							// Es k�nnen alle Daten mit dem StartTelegramm verschickt werden
							final byte[] startBigTelegram = new byte[_dataBytesLeft + 9];
							final int dataIndex = createTelegramFrame(startBigTelegram);
							System.arraycopy(_wholeTelegram, _dataPosition, startBigTelegram, dataIndex, _dataBytesLeft);

							_dataPosition = _wholeTelegram.length - 1;

							_dataBytesLeft = 0;

							// Das erste Telegramm wurde verschickt
							_startBlockSend = true;

							return startBigTelegram;
						}
					}
					else {
						// Das Starttelegramm wurde verschickt, also kann als n�chstes nur ein BigPiece oder
						// BigEnd verschickt werden

						if(sendEndBlock() == false) {
							// Es kann noch ein BigPiece verschickt werden

							// wieviele Daten k�nnen verschickt werden

							if(_dataBytesLeft > (_maxDataForTelegram - 7)) {

								// Es sind gen�gend Daten vorhanden, das St�ck wird ganz ben�tigt
								final byte[] bigPieceTelegram = new byte[_maxDataForTelegram];
								final int dataIndex = createTelegramFrame(bigPieceTelegram);
								System.arraycopy(_wholeTelegram, _dataPosition, bigPieceTelegram, dataIndex, (_maxDataForTelegram - 7));

								// Den Zeiger auf dem zu kopierenden Byte-Array weiterf�hren
								_dataPosition = _dataPosition + (_maxDataForTelegram - 7);

								// Es wurden _maxDataForTelegram - 7 viele Byte �bertragen
								_dataBytesLeft = _dataBytesLeft - (_maxDataForTelegram - 7);

								return bigPieceTelegram;
							}
							else {
								// Es wird nur ein Teil des Pieces ben�tigt

								final byte[] bigPieceTelegram = new byte[_dataBytesLeft + 7];
								final int dataIndex = createTelegramFrame(bigPieceTelegram);
								System.arraycopy(_wholeTelegram, _dataPosition, bigPieceTelegram, dataIndex, _dataBytesLeft);

								// Es gibt nichts mehr zu kopieren
								_dataPosition = _wholeTelegram.length - 1;

								// Es wurden alle Daten verschickt
								_dataBytesLeft = 0;

								return bigPieceTelegram;
							}
						}
						else {
							// Es ist kein BigPiece mehr �brig, also muss der EndBlock verschickt werden

							// Kommen noch Daten in den Endblock

							if(_dataBytesLeft > 0) {

								final byte[] bigEndTelegram = new byte[_dataBytesLeft + 8];
								final int dataIndex = createTelegramFrame(bigEndTelegram);
								System.arraycopy(_wholeTelegram, _dataPosition, bigEndTelegram, dataIndex, _dataBytesLeft);

								// Es gibt nichts mehr zu kopieren
								_dataPosition = _wholeTelegram.length - 1;

								// Es wurden alle Daten verschickt
								_dataBytesLeft = 0;

								// Alle Daten wurden verschickt
								_onePieceLeft = false;

								return bigEndTelegram;
							}
							else {
								// Es gibt keine Daten mehr
								final byte[] bigEndTelegram = new byte[8];
								final int dataIndex = createTelegramFrame(bigEndTelegram);

								// Es gibt nichts mehr zu kopieren
								_dataPosition = _wholeTelegram.length - 1;

								// Es wurden alle Daten verschickt
								_dataBytesLeft = 0;

								// Alle Daten wurden verschickt
								_onePieceLeft = false;

								return bigEndTelegram;
							}
						}
					}
				}
				else if(_telegramType == LongTelegramType.BIGGER) {

					// Es kann 3 F�lle geben, das erste Telegramm(BiggerStart) soll verschickt werden, ein NextBiggerPiece soll verschickt werden oder
					// es wird das BiggerEndpaket verschickt.

					_telegramNumber++;

					if(_startBlockSend == false) {

						// Der BiggerStartBlock wurde noch nicht verschickt. Es werden soviele Daten wie m�glich
						// verschickt.

						if(_dataBytesLeft > (_maxDataForTelegram - _costStartBiggerBlock)) {
							// Es gehen nicht alle Daten in den Startblock (Normalfall)
							final byte[] startBiggerTelegram = new byte[_maxDataForTelegram];
							final int dataIndex = createTelegramFrame(startBiggerTelegram);

							System.arraycopy(_wholeTelegram, _dataPosition, startBiggerTelegram, dataIndex, (_maxDataForTelegram - _costStartBiggerBlock));

							_dataPosition = _dataPosition + (_maxDataForTelegram - _costStartBiggerBlock);

							_dataBytesLeft = _dataBytesLeft - (_maxDataForTelegram - _costStartBiggerBlock);

							// Das erste Telegramm wurde verschickt
							_startBlockSend = true;

							return startBiggerTelegram;
						}
						else {
							// Es k�nnen alle Daten mit dem StartTelegramm verschickt werden
							final byte[] startBiggerTelegram = new byte[_dataBytesLeft + _costStartBiggerBlock];
							final int dataIndex = createTelegramFrame(startBiggerTelegram);
							System.arraycopy(_wholeTelegram, _dataPosition, startBiggerTelegram, dataIndex, _dataBytesLeft);

							_dataPosition = _wholeTelegram.length - 1;

							_dataBytesLeft = 0;

							// Das erste Telegramm wurde verschickt
							_startBlockSend = true;

							return startBiggerTelegram;
						}
					}
					else {
						// Das Starttelegramm wurde verschickt, also kann als n�chstes nur ein BigPiece oder
						// BigEnd verschickt werden

						if(sendEndBlock() == false) {
							// Es kann noch ein BiggerPiece verschickt werden

							// wieviele Daten k�nnen verschickt werden

							if(_dataBytesLeft > (_maxDataForTelegram - _costNextBiggerPiece)) {

								// Es sind gen�gend Daten vorhanden, das St�ck wird ganz ben�tigt
								final byte[] biggerPieceTelegram = new byte[_maxDataForTelegram];
								final int dataIndex = createTelegramFrame(biggerPieceTelegram);
								System.arraycopy(_wholeTelegram, _dataPosition, biggerPieceTelegram, dataIndex, (_maxDataForTelegram - _costNextBiggerPiece));

								// Den Zeiger auf dem zu kopierenden Byte-Array weiterf�hren
								_dataPosition = _dataPosition + (_maxDataForTelegram - _costNextBiggerPiece);

								_dataBytesLeft = _dataBytesLeft - (_maxDataForTelegram - _costNextBiggerPiece);

								return biggerPieceTelegram;
							}
							else {
								// Es wird nur ein Teil des Pieces ben�tigt

								final byte[] biggerPieceTelegram = new byte[_dataBytesLeft + _costNextBiggerPiece];
								final int dataIndex = createTelegramFrame(biggerPieceTelegram);
								System.arraycopy(_wholeTelegram, _dataPosition, biggerPieceTelegram, dataIndex, _dataBytesLeft);

								// Es gibt nichts mehr zu kopieren
								_dataPosition = _wholeTelegram.length - 1;

								// Es wurden alle Daten verschickt
								_dataBytesLeft = 0;

								return biggerPieceTelegram;
							}
						}
						else {
							// Es ist kein BiggerPiece mehr �brig, also muss der EndBlock verschickt werden

							// Kommen noch Daten in den Endblock

							if(_dataBytesLeft > 0) {

								final byte[] biggerEndTelegram = new byte[_dataBytesLeft + _costEndBiggerBlock];
								final int dataIndex = createTelegramFrame(biggerEndTelegram);
								System.arraycopy(_wholeTelegram, _dataPosition, biggerEndTelegram, dataIndex, _dataBytesLeft);

								// Es gibt nichts mehr zu kopieren
								_dataPosition = _wholeTelegram.length - 1;

								// Es wurden alle Daten verschickt
								_dataBytesLeft = 0;

								// Alle Daten wurden verschickt
								_onePieceLeft = false;

								return biggerEndTelegram;
							}
							else {
								// Es gibt keine Daten mehr
								final byte[] biggerEndTelegram = new byte[_costEndBiggerBlock];
								final int dataIndex = createTelegramFrame(biggerEndTelegram);

								// Es gibt nichts mehr zu kopieren
								_dataPosition = _wholeTelegram.length - 1;

								// Es wurden alle Daten verschickt
								_dataBytesLeft = 0;

								// Alle Daten wurden verschickt
								_onePieceLeft = false;

								return biggerEndTelegram;
							}
						}
					}
				}
				else {
					throw new IllegalStateException("Das Telegramm ist wieder ein ShortBlock, noch BigBlock oder BiggerBlock: " + _telegramType);
				}
			} // synch
		}

		/**
		 * Diese Methode gibt true zur�ck, wenn noch ein Teil-Telegramm zur Verf�gung steht. Steht kein Teil-Telegramm mehr zur Verf�gung, dann wird false
		 * zur�ckgegeben.
		 *
		 * @return s.o.
		 */
		public boolean isOnePieceLeft() {
			synchronized(this) {
				return _onePieceLeft;
			}
		}

		/**
		 * Erzeugt einen Header und tr�gt den richtigen Typ (ShortBlock, StartBig,EndBig, usw ein) Danach k�nnen in das Array die Bytes des Datenblocks eingef�gt
		 * werden, CRC wird ebenfalls eingetragen.
		 *
		 * @param byteArray s.o.
		 *
		 * @return ab welcher Position k�nnen Daten eingef�gt werden
		 */
		private int createTelegramFrame(byte[] byteArray) {
			synchronized(this) {
				// Senderknotennummer in das Byte-Array eintragen
				createSenderNodeNumber(byteArray);
				// 0 Eintragen
				byteArray[3] = 0;

				// Ab welchem Index d�rfen Daten eingef�gt werden
				final int dataIndex;

				// Von welchem Typ ist das Telegramm (ShortBlock, StartBig, StartBigger, usw. ...)
				if(_telegramType == LongTelegramType.SHORT_BLOCK) {
					// Es soll ein ShortBlock verschickt werden

					// Bit 4 enth�lt eine "1", Bit 0 bis 3 enthalten den Pakettyp
					byteArray[4] = (byte)(0x10 + _telegramType.getTypeNumber());

					dataIndex = 6;
				}
				else if(_telegramType == LongTelegramType.BIG) {
					// Es soll ein Big Teilst�ck verschickt werden. Was ist das f�r ein St�ck ?
					// StartBigBlock oder NextBigPiece oder EndBigBlock

					if(_startBlockSend == false) {
						// Der Startblock wurde noch nicht verschickt

						// Bit 4 enth�lt eine "1", Bit 0 bis 3 enthalten den Pakettyp
						byteArray[4] = (byte)(0x10 + LongTelegramType.START_BIG_BLOCK.getTypeNumber());

						// L�nge des kompletten Datenblocks speichern
						final int sizeOfWholeData = _wholeTelegram.length;

						// High-Byte
						byteArray[6] = (byte)((sizeOfWholeData & 0x0000FF00) >> 8);

						// Low-Byte
						byteArray[7] = (byte)(sizeOfWholeData & 0x000000FF);

						// Anzahl St�cke gesamt (Start und End z�hlen nicht)
						byteArray[8] = (byte)(_numberOfNeededPieces & 0x000000FF);

						// Ein StartBigBlock ist fertig, die Daten k�nnen ab Index 9 eingef�gt werden
						dataIndex = 9;
					}
					else {
						// Der Startblock wurde schon verschickt, soll ein NextBigPiece oder schon
						// das Schlusspaket verschickt werden ?

						if(sendEndBlock() == true) {
							// Es muss der letzte Block verschickt werden
							byteArray[4] = (byte)(0x10 + LongTelegramType.END_BIG_BLOCK.getTypeNumber());

							// Ab dieser Position d�rfen die Daten stehen
							dataIndex = 6;

							// Es muss die CRC Pr�fsumme eingef�gt werden, diese steht in den letzten 2
							// Bytes des Byte-Arrays

							// Vorletzte Position im Byte-Array, high Byte
							byteArray[byteArray.length - 2] = ((_crcChecksum & 0x0000FF00) >> 8);
							// Letzte Position im Byte-Array, low Byte
							byteArray[byteArray.length - 1] = (_crcChecksum & 0x000000FF);
						}
						else {

							// Es ist ein NextBigPiece Telegramm
							byteArray[4] = (byte)(0x10 + LongTelegramType.NEXT_BIG_PIECE.getTypeNumber());

							// Die Nummer des Datensatzes
							byteArray[6] = (byte)_telegramNumber;

							// Ab dieser Stelle k�nnen Daten stehen
							dataIndex = 7;
						}
					}
				}
				else if(_telegramType == LongTelegramType.BIGGER) {
					if(_startBlockSend == false) {
						// Der Startblock wurde noch nicht verschickt

						// Bit 4 enth�lt eine "1", Bit 0 bis 3 enthalten den Pakettyp
						byteArray[4] = (byte)(0x10 + LongTelegramType.START_BIGGER_BLOCK.getTypeNumber());

						// L�nge des kompletten Datenblocks speichern
						final int sizeOfWholeData = _wholeTelegram.length;

						// Extra High-Byte
						byteArray[6] = (byte)((sizeOfWholeData & 0x00FF0000) >> 16);

						// High-Byte
						byteArray[7] = (byte)((sizeOfWholeData & 0x0000FF00) >> 8);

						// Low-Byte
						byteArray[8] = (byte)((sizeOfWholeData & 0x000000FF));

						// Anzahl St�cke gesamt (Start und End z�hlen nicht)

						// High
						byteArray[9] = (byte)((_numberOfNeededPieces & 0x0000FF00) >> 8);

						// low
						byteArray[10] = (byte)(_numberOfNeededPieces & 0x000000FF);

						dataIndex = 11;
					}
					else {
						// Der Startblock wurde schon verschickt, soll ein NextBigPiece oder schon
						// das Schlusspaket verschickt werden ?

						if(sendEndBlock() == true) {
							// Es muss der letzte Block verschickt werden
							byteArray[4] = (byte)(0x10 + LongTelegramType.END_BIGGER_BLOCK.getTypeNumber());

							// Ab dieser Position k�nnen Daten stehen
							dataIndex = 6;

							// Die letzten 4 Bytes des Byte-Arrays sind der CRC Pr�fsumme vorbehalten

							// ultra high Byte
							byteArray[byteArray.length - 4] = ((_crcChecksum & 0xFF000000) >> 24);
							// extra high Byte
							byteArray[byteArray.length - 3] = ((_crcChecksum & 0x00FF0000) >> 16);
							// high Byte
							byteArray[byteArray.length - 2] = ((_crcChecksum & 0x0000FF00) >> 8);
							// low Byte
							byteArray[byteArray.length - 1] = (_crcChecksum & 0x000000FF);
						}
						else {
							// Es ist ein NextBiggerPiece Telegramm
							byteArray[4] = (byte)(0x10 + LongTelegramType.NEXT_BIGGER_PIECE.getTypeNumber());

							// Nummer des Datenblockst�cks

							// High
							byteArray[6] = (byte)((_telegramNumber & 0x0000FF00) >> 8);

							// Low
							byteArray[7] = (byte)(_telegramNumber & 0x000000FF);

							dataIndex = 8;
						}
					}
				}
				else {
					// Unbekannter Typ, das kann nicht vorkommen
					throw new IllegalStateException("Der Typ des Telegramms kann nicht identifiziert werden: " + _telegramType);
				}

				// DatenBlockTyp und DatenBlockNummer eintragen, diese Eintr�ge k�nnen wieder bei allen Typen
				// gleich behandelt werden.

				// Eine "1" im Bit 4, in den Bits 0 bis 3 steht die DatenBlockNummer
				byteArray[5] = (byte)(0x10 + _dataBlockNumber);

				return dataIndex;
			}
		}

		/**
		 * Diese Methode stellt fest, ob das zu verschickende Telegramm das EndBigBlock oder EndBiggerBlock sein muss.
		 *
		 * @return true = das Telegramm muss das EndBlock(Big oder Bigger) sein; false = das Telegramm muss ein NextPiece (Big oder Bigger) sein
		 */
		private boolean sendEndBlock() {
			synchronized(this) {
				if(_telegramNumber == (_numberOfNeededPieces + 1)) {
					return true;
				}
				else {
					return false;
				}
			}
		}

		/**
		 * Diese Methode tr�gt in dem �bergebenen Byte-Array die Knotennummer des Senders ein
		 *
		 * @param byteArray s.o.
		 */
		private void createSenderNodeNumber(byte[] byteArray) {
			// Alles l�schen, bis auf die ersten 8 Bit (low-Byte)
			byteArray[0] = (byte)(_senderNodeNumber & 0x000000FF);
			// Alles l�schen bis auf die high-Byte und shift nach rechts
			byteArray[1] = (byte)((_senderNodeNumber & 0x0000FF00) >> 8);
			// Alles l�schen bis auf die extra High-Bytes und shift nach rechts
			byteArray[2] = (byte)((_senderNodeNumber & 0x00FF0000) >> 16);
		}

		public byte getDataBlockNumber() {
			synchronized(this) {
				return _dataBlockNumber;
			}
		}

		public int getReceiverNodeNumber() {
			synchronized(this) {
				return _receiverNodeNumber;
			}
		}

		public int getSenderNodeNumber() {
			synchronized(this) {
				return _senderNodeNumber;
			}
		}

		public int getPriority() {
			synchronized(this) {
				return _priority;
			}
		}
	}
}

