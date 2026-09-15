package com.bookrush.analytics.features;
import static org.junit.jupiter.api.Assertions.*; import java.util.*; import org.junit.jupiter.api.Test;
class StyleVectorCalculatorTest { @Test void preservesFeatureOrderAndStandardizes(){var n=new StyleVectorCalculator.Normalization("pt","fixture",List.of("a","b"),new double[]{1,10},new double[]{1,2}); var v=StyleVectorCalculator.calculate(Map.of("b",14d,"a",2d),n); assertArrayEquals(new float[]{1,2},v);} }
