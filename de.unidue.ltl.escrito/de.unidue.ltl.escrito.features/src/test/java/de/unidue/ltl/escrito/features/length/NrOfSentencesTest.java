package de.unidue.ltl.escrito.features.length;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngine;
import static org.dkpro.tc.testing.FeatureTestUtil.assertFeature;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.jcas.JCas;
import org.dkpro.tc.api.features.Feature;
import org.dkpro.tc.api.type.TextClassificationTarget;
import org.junit.Test;

import de.tudarmstadt.ukp.dkpro.core.tokit.BreakIteratorSegmenter;
import org.junit.Assert;

public class NrOfSentencesTest
{
    @Test
    public void nrOfSentencesFeatureExtractorTest()
        throws Exception
    {
        AnalysisEngine engine = createEngine(BreakIteratorSegmenter.class);

        JCas jcas = engine.newJCas();
        jcas.setDocumentLanguage("en");
        jcas.setDocumentText("This is a test! Does it test sentences? Oh yes, it does!");
        engine.process(jcas);

        TextClassificationTarget target = new TextClassificationTarget(jcas, 0,
                jcas.getDocumentText().length());

        NrOfSentences extractor = new NrOfSentences();
        List<Feature> features = new ArrayList<Feature>(extractor.extract(jcas, target));

        Assert.assertEquals(1, features.size());

        Iterator<Feature> iter = features.iterator();
        assertFeature(NrOfSentences.FN_NR_OF_SENTENCES, 3, iter.next());
    }
}