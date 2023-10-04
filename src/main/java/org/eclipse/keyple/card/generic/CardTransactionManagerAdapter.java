/* **************************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.card.generic;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.keyple.core.util.ApduUtil;
import org.eclipse.keyple.core.util.Assert;
import org.eclipse.keyple.core.util.HexUtil;
import org.eclipse.keypop.card.*;
import org.eclipse.keypop.card.spi.ApduRequestSpi;
import org.eclipse.keypop.reader.CardReader;
import org.eclipse.keypop.reader.selection.spi.SmartCard;

/**
 * Implementation of {@link CardTransactionManager}.
 *
 * @since 2.0.0
 */
class CardTransactionManagerAdapter implements CardTransactionManager {

  public static final String APDU_COMMAND = "apduCommand";
  private final CardReader reader;
  private final List<ApduRequestSpi> apduRequests;

  /**
   * Creates an instance of {@link CardTransactionManager}.
   *
   * @param reader The reader through which the card communicates.
   * @param card The initial card data provided by the selection process.
   * @throws IllegalArgumentException If the card resource or one of its components is null.
   * @since 2.0.0
   */
  CardTransactionManagerAdapter(CardReader reader, SmartCard card) {
    Assert.getInstance().notNull(reader, "reader").notNull(card, "card");
    this.reader = reader;
    apduRequests = new ArrayList<ApduRequestSpi>();
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public CardTransactionManager prepareApdu(String apduCommand) {

    Assert.getInstance()
        .notEmpty(apduCommand, APDU_COMMAND)
        .isTrue(HexUtil.isValid(apduCommand), APDU_COMMAND);

    prepareApdu(HexUtil.toByteArray(apduCommand));
    return this;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public CardTransactionManager prepareApdu(byte[] apduCommand) {

    Assert.getInstance()
        .notNull(apduCommand, APDU_COMMAND)
        .isInRange(apduCommand.length, 5, 251, "length");

    apduRequests.add(new ApduRequestAdapter(apduCommand));
    return this;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public CardTransactionManager prepareApdu(
      byte cla, byte ins, byte p1, byte p2, byte[] dataIn, Byte le) {
    apduRequests.add(new ApduRequestAdapter(ApduUtil.build(cla, ins, p1, p2, dataIn, le)));
    return this;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public List<byte[]> processApdusToByteArrays(ChannelControl channelControl)
      throws TransactionException {
    CardResponseApi cardResponse;
    if (apduRequests.isEmpty()) {
      return new ArrayList<byte[]>(0);
    }
    try {
      cardResponse =
          ((ProxyReaderApi) reader)
              .transmitCardRequest(
                  new CardRequestAdapter(apduRequests, false),
                  channelControl == ChannelControl.CLOSE_AFTER
                      ? org.eclipse.keypop.card.ChannelControl.CLOSE_AFTER
                      : org.eclipse.keypop.card.ChannelControl.KEEP_OPEN);
    } catch (ReaderBrokenCommunicationException e) {
      throw new TransactionException("Reader communication error.", e);
    } catch (CardBrokenCommunicationException e) {
      throw new TransactionException("Card communication error.", e);
    } catch (UnexpectedStatusWordException e) {
      throw new TransactionException("Apdu error.", e);
    } finally {
      apduRequests.clear();
    }
    List<byte[]> apduResponsesBytes = new ArrayList<byte[]>();
    for (ApduResponseApi apduResponse : cardResponse.getApduResponses()) {
      apduResponsesBytes.add(apduResponse.getApdu());
    }
    return apduResponsesBytes;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.0
   */
  @Override
  public List<String> processApdusToHexStrings(ChannelControl channelControl)
      throws TransactionException {
    List<byte[]> apduResponsesBytes = processApdusToByteArrays(channelControl);
    List<String> apduResponsesHex = new ArrayList<String>();
    for (byte[] bytes : apduResponsesBytes) {
      apduResponsesHex.add(HexUtil.toHex(bytes));
    }
    return apduResponsesHex;
  }
}
