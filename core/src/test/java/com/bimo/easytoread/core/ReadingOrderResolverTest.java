package com.bimo.easytoread.core;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ReadingOrderResolverTest {
    private final ReadingOrderResolver resolver = new ReadingOrderResolver();

    @Test
    public void readsThreeColumnsTopToBottomThenLeftToRight() {
        ReadingOrderResolver.Result result = resolver.resolve(Arrays.asList(
                line("R2", 210, 34, 270, 46),
                line("M1", 110, 10, 170, 22),
                line("L2", 10, 34, 70, 46),
                line("R1", 210, 10, 270, 22),
                line("L1", 10, 10, 70, 22),
                line("M2", 110, 34, 170, 46)
        ));

        assertEquals(ReadingOrderResolver.LayoutType.MULTI_COLUMN, result.getLayoutType());
        assertTexts(result.getLines(), "L1", "L2", "M1", "M2", "R1", "R2");
    }

    @Test
    public void readsWideHeadlineBeforeNewspaperColumns() {
        ReadingOrderResolver.Result result = resolver.resolve(Arrays.asList(
                line("Right body 2", 180, 58, 280, 70),
                line("Left body 1", 10, 34, 110, 46),
                line("NEWS HEADLINE", 10, 4, 280, 24),
                line("Right body 1", 180, 34, 280, 46),
                line("Left body 2", 10, 58, 110, 70)
        ));

        assertEquals(ReadingOrderResolver.LayoutType.MULTI_COLUMN, result.getLayoutType());
        assertTexts(
                result.getLines(),
                "NEWS HEADLINE",
                "Left body 1",
                "Left body 2",
                "Right body 1",
                "Right body 2"
        );
    }

    @Test
    public void singleColumnPreservesTopToBottomOrder() {
        ReadingOrderResolver.Result result = resolver.resolve(Arrays.asList(
                line("Third", 10, 50, 250, 62),
                line("First", 10, 10, 250, 22),
                line("Second", 20, 30, 245, 42)
        ));

        assertEquals(ReadingOrderResolver.LayoutType.SINGLE_COLUMN, result.getLayoutType());
        assertTexts(result.getLines(), "First", "Second", "Third");
    }

    @Test
    public void posterPromotesProminentHeadlineBeforeSmallUtilityText() {
        ReadingOrderResolver.Result result = resolver.resolve(Arrays.asList(
                line("Small sponsor", 10, 4, 120, 14),
                line("MAIN EVENT", 20, 28, 270, 58),
                line("Saturday at 19.00", 20, 90, 220, 102),
                line("City Hall", 20, 120, 140, 132)
        ));

        assertEquals(ReadingOrderResolver.LayoutType.POSTER, result.getLayoutType());
        assertTexts(
                result.getLines(),
                "MAIN EVENT",
                "Small sponsor",
                "Saturday at 19.00",
                "City Hall"
        );
    }

    @Test
    public void gridRowsIgnoreSmallVerticalJitterAndReadLeftToRight() {
        ReadingOrderResolver.Result result = resolver.resolve(Arrays.asList(
                line("Thu", 210, 12, 250, 24),
                line("Tue", 75, 8, 115, 20),
                line("Mon", 10, 10, 50, 22),
                line("Wed", 140, 11, 190, 23),
                line("4", 210, 40, 230, 52),
                line("2", 75, 38, 95, 50),
                line("1", 10, 39, 30, 51),
                line("3", 140, 41, 160, 53)
        ));

        assertEquals(ReadingOrderResolver.LayoutType.GRID, result.getLayoutType());
        assertTexts(result.getLines(), "Mon", "Tue", "Wed", "Thu", "1", "2", "3", "4");
    }

    private static DetectedLine line(String text, int left, int top, int right, int bottom) {
        return new DetectedLine(text, new Box(left, top, right, bottom), 0.95f);
    }

    private static void assertTexts(List<DetectedLine> lines, String... expected) {
        assertEquals(expected.length, lines.size());
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], lines.get(index).getText());
        }
    }
}
