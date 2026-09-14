package com.bookrush.analytics.features;
import static org.junit.jupiter.api.Assertions.*; import java.util.List; import org.junit.jupiter.api.Test;
class PrototypeClassifierTest { @Test void picksClosestPrototypeOffline(){ var r=PrototypeClassifier.classify(new float[]{1,0},List.of(new SemanticPrototype("romance","v1",new float[]{1,0}),new SemanticPrototype("politics","v1",new float[]{0,1}))); assertEquals("romance",r.topic()); assertEquals("v1",r.prototypeVersion()); } }
