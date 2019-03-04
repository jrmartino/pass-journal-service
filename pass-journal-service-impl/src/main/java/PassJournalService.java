/*
 *
 * Copyright 2019 Johns Hopkins University
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */


import org.dataconservancy.pass.model.Journal;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.StringReader;


/**
 * @author jrm
 */
public class PassJournalService {
    private static final String XREF_MESSAGE = "message";
    private static final String XREF_TITLE = "container-title";
    private static final String XREF_ISSN_TYPE_ARRAY = "issn-type";
    private static final String XREF_ISSN_TYPE = "type";
    private static final String XREF_ISSN_VALUE = "value";


    Journal buildPassJournal(String jsonInput) {

        Journal  passJournal = new Journal();

        JsonReader jsonReader;
        jsonReader = Json.createReader(new StringReader(jsonInput));
        JsonObject crossrefMetadata = jsonReader.readObject();
        JsonObject messageObject = crossrefMetadata.getJsonObject(XREF_MESSAGE);
        JsonArray containerTitleArray = messageObject.getJsonArray(XREF_TITLE);
        JsonArray issnTypeArray = messageObject.getJsonArray(XREF_ISSN_TYPE_ARRAY);

        if (!containerTitleArray.isNull(0)) {
            passJournal.setName(containerTitleArray.getString(0));
        }

        for (int i=0; i < issnTypeArray.size(); i++) {
            JsonObject issn = issnTypeArray.getJsonObject(i);
            
            String type="";

            //translate crossref type strings to PASS type strings
            if (IssnType.PRINT.getCrossrefTypeString().equals(issn.getString(XREF_ISSN_TYPE))) {
                type = IssnType.PRINT.getPassTypeString();
            } else if (IssnType.ELECTRONIC.getCrossrefTypeString().equals(issn.getString(XREF_ISSN_TYPE))) {
                type = IssnType.ELECTRONIC.getPassTypeString();
            }

            //collect the value for this issn
            String value = issn.getString(XREF_ISSN_VALUE);

            if (value.length() > 0) {
                passJournal.getIssns().add(String.join(":", type, value));
            }

        }

        return passJournal;
    }


}
