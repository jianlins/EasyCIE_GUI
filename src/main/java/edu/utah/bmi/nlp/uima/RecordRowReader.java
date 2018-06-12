
/*
 * Copyright  2017  Department of Biomedical Informatics, University of Utah
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.utah.bmi.nlp.uima;

import edu.utah.bmi.nlp.uima.reader.StringMetaReader;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;

import java.io.IOException;

/**
 * @author Jianlin Shi
 * Created on 7/13/16.
 */

/**
 * This is a simple String ArrayList reader to convert a String ArrayList to UIMA Jcas.
 * It is convenient class for testing.
 */
public class RecordRowReader extends StringMetaReader {


    public void getNext(CAS aCAS) throws CollectionException {
        String metaInfor = meta;
        JCas jcas;
        try {
            jcas = aCAS.getJCas();
        } catch (CASException var6) {
            throw new CollectionException(var6);
        }
        jcas.setDocumentText(input);
        SourceDocumentInformation srcDocInfo = new SourceDocumentInformation(jcas, 0, input.length());
        srcDocInfo.setUri(metaInfor);
        srcDocInfo.setOffsetInSource(0);
        srcDocInfo.setDocumentSize(input.length());
        srcDocInfo.setLastSegment(true);
        srcDocInfo.addToIndexes();

        // set language if it was explicitly specified as a configuration parameter
        if (mLanguage != null) {
            jcas.setDocumentLanguage(mLanguage);
        }
        mCurrentIndex++;
    }
}
