package io.paymenthighway.model.request;

import io.paymenthighway.model.Token;

/**
 * Transaction request POJO
 */
public class TransactionRequest extends Request {

  String amount = null;
  String currency = null;
  Token token = null;
  Card card = null;
  boolean blocking = true;
  String order = null;

  public TransactionRequest(Token token, String amount, String currency) {
    this.token = token;
    this.amount = amount;
    this.currency = currency;
  }

  public TransactionRequest(Token token, String amount, String currency, String order) {
    this.token = token;
    this.amount = amount;
    this.currency = currency;
    this.order = order;
  }

  public TransactionRequest(Token token, String amount, String currency, boolean blocking) {
    this.token = token;
    this.amount = amount;
    this.currency = currency;
    this.blocking = blocking;
  }

  public TransactionRequest(Token token, String amount, String currency, boolean blocking, String order) {
    this.token = token;
    this.amount = amount;
    this.currency = currency;
    this.blocking = blocking;
    this.order = order;
  }

  public TransactionRequest(Card card, String amount, String currency) {
    this.card = card;
    this.amount = amount;
    this.currency = currency;
  }

  public TransactionRequest(Card card, String amount, String currency, String order) {
    this.card = card;
    this.amount = amount;
    this.currency = currency;
    this.order = order;
  }

  public TransactionRequest(Card card, String amount, String currency, boolean blocking) {
    this.card = card;
    this.amount = amount;
    this.currency = currency;
    this.blocking = blocking;
  }

  public TransactionRequest(Card card, String amount, String currency, boolean blocking, String order) {
    this.card = card;
    this.amount = amount;
    this.currency = currency;
    this.blocking = blocking;
    this.order = order;
  }

  public String getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public boolean isBlocking() {
    return blocking;
  }

  public Card getCard() {
    return card;
  }

  public Token getToken() {
    return token;
  }

  public String getOrder() {
    return order;
  }
}
