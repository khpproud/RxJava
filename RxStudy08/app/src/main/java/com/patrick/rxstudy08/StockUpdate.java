package com.patrick.rxstudy08;

import com.patrick.rxstudy08.stock.jsonv2.StockData;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import twitter4j.Status;

/**
 * 주식 데이터 값 객체 샘플
 */
public class StockUpdate implements Serializable {
    private final String stockSymbol;
    private final BigDecimal price;
    private final Date date;
    private Integer id;                     // DB용 id

    private final String twitterStatus;     // Twitter Text

    public StockUpdate(String stockSymbol, BigDecimal price, Date date, String twitterStatus) {
        if (stockSymbol == null)
            stockSymbol = "";
        if (twitterStatus == null)
            twitterStatus = "";
        this.stockSymbol = stockSymbol;
        this.price = price;
        this.date = date;
        this.twitterStatus = twitterStatus;
    }

    public String getStockSymbol() {
        return stockSymbol;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Date getDate() {
        return date;
    }

    public Integer getId() { return id; }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTwitterStatus() { return twitterStatus; }

    // StockData 업데이트 용
    public static StockUpdate create(StockData sq) {
        return new StockUpdate(sq.getSymbol(), new BigDecimal(sq.getPrice()),
                sq.getLastTradeTime(), "");
    }

    // TwitterStatus 업데이트 용
    public static StockUpdate create(Status status) {
        return new StockUpdate("", BigDecimal.ZERO, status.getCreatedAt(),
                status.getText());
    }

    // Stock 업데이트 인지 Status 업데이트 인지 확인
    public boolean isTwitterStatusUpdate() {
        return !twitterStatus.isEmpty();
    }
}
