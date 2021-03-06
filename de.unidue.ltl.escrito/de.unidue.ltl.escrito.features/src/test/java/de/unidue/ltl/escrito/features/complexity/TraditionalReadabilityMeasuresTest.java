package de.unidue.ltl.escrito.features.complexity;

import static org.dkpro.tc.testing.FeatureTestUtil.assertFeature;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngine;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.jcas.JCas;
import org.junit.Assert;
import org.junit.Test;
import de.tudarmstadt.ukp.dkpro.core.tokit.BreakIteratorSegmenter;
import org.dkpro.tc.api.features.Feature;
import org.dkpro.tc.api.features.util.FeatureUtil;
import org.dkpro.tc.api.type.TextClassificationTarget;

public class TraditionalReadabilityMeasuresTest
{

    @Test
    public void readabilityFeatureExtractorTest()
        throws Exception
    {
        // HashMap<String, Double> correctResult = new HashMap<String, Double>();
        // correctResult.put("kincaid", 7.6);
        // correctResult.put("ari", 9.1);
        // correctResult.put("coleman_liau", 11.6);
        // correctResult.put("flesch", 70.6);
        // correctResult.put("lix", 5.0);
        // correctResult.put("smog", 9.9);
        // correctResult.put("fog", 10.6);

        String testDocument = FileUtils.readFileToString(new File("src/test/resources/test_document_en.txt"));

        AnalysisEngineDescription desc = createEngineDescription(BreakIteratorSegmenter.class);
        AnalysisEngine engine = createEngine(desc);

        JCas jcas = engine.newJCas();
        jcas.setDocumentLanguage("en");
        jcas.setDocumentText(testDocument);
        engine.process(jcas);

        TraditionalReadabilityMeasures extractor = FeatureUtil.createResource(
        		TraditionalReadabilityMeasures.class,
        		TraditionalReadabilityMeasures.PARAM_ADD_COLEMANLIAU, "true",
        		TraditionalReadabilityMeasures.PARAM_UNIQUE_EXTRACTOR_NAME, "dummy");

        List<Feature> features = new ArrayList<>(extractor.extract(jcas, TextClassificationTarget.get(jcas)));

        Assert.assertEquals(2, features.size());
        Iterator<Feature> iter = features.iterator();
        assertFeature(TraditionalReadabilityMeasures.PARAM_ADD_KINCAID, 7.6, iter.next(), 0.1);
        assertFeature(TraditionalReadabilityMeasures.PARAM_ADD_COLEMANLIAU, 11.6, iter.next(), 0.1);

    }
}