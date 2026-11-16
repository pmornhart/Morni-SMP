package net.morni.smp.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void formatsGermanAndEnglish() {
        assertEquals("0 ₮", Money.format(0, "de"));
        assertEquals("12 ₮", Money.format(1200, "de"));
        assertEquals("12,50 ₮", Money.format(1250, "de"));
        assertEquals("0,05 ₮", Money.format(5, "de"));
        assertEquals("1.234.567,89 ₮", Money.format(123456789, "de"));
        assertEquals("1,234,567.89 ₮", Money.format(123456789, "en"));
        assertEquals("-3,10 ₮", Money.format(-310, "de"));
    }

    @Test
    void parsesPlayerInput() {
        assertEquals(1200, Money.parse("12"));
        assertEquals(1250, Money.parse("12,5"));
        assertEquals(1250, Money.parse("12.50"));
        assertEquals(150000, Money.parse("1.500"));
        assertEquals(150000, Money.parse("1,500"));
        assertEquals(123456789, Money.parse("1.234.567,89"));
        assertEquals(123456789, Money.parse("1,234,567.89"));
        assertEquals(100000, Money.parse("1k"));
        assertEquals(250000, Money.parse("2,5k"));
        assertEquals(100000000, Money.parse("1m"));
        assertEquals(500, Money.parse("5 ₮"));
        assertEquals(500, Money.parse("5taler"));
    }

    @Test
    void rejectsNonsense() {
        assertEquals(-1, Money.parse(""));
        assertEquals(-1, Money.parse("abc"));
        assertEquals(-1, Money.parse("-5"));
        assertEquals(-1, Money.parse("0"));
        assertEquals(-1, Money.parse("1,2345"));
        assertEquals(-1, Money.parse("1.2.3"));
        assertEquals(-1, Money.parse("99999999999999"));
        assertEquals(-1, Money.parse(null));
    }
}
