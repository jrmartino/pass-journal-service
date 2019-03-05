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


import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.PassClientFactory;
import org.dataconservancy.pass.model.Journal;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.StringReader;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * @author jrm
 */
public class PassJournalService {
    private static final String XREF_MESSAGE = "message";
    private static final String XREF_TITLE = "container-title";
    private static final String XREF_ISSN_TYPE_ARRAY = "issn-type";
    private static final String XREF_ISSN_TYPE = "type";
    private static final String XREF_ISSN_VALUE = "value";

    PassClient passClient = PassClientFactory.getPassClient();


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

    Journal updateJournalinPass(Journal journal) {
        List<String> issns = journal.getIssns();

        URI passJournalUri = null;
        Journal passJournal;

        for (String issn : issns) {
            passJournalUri = passClient.findByAttribute(Journal.class, "issns", issn);
            if (passJournalUri != null) {
                break;
            }
        }

        if (passJournalUri == null) {//we don't have this journal in pass yet - let's put this one in
            passJournal = passClient.createAndReadResource(journal, Journal.class);
        } else { //we have a journal, let's see if we can add anything new - title or issns. we add only if not present
            boolean update = false;
            passJournal = passClient.readResource(passJournalUri, Journal.class);
            if (passJournal != null) {

                //check to see if we can supply a journal name
                if ((passJournal.getName() == null || passJournal.getName().isEmpty()) && !journal.getName().isEmpty()) {
                    passJournal.setName(journal.getName());
                    update = true;
                }

                //check to see if we can supply issns
                if (!passJournal.getIssns().containsAll(journal.getIssns())) {
                    List<String> newIssnList = Stream.concat(passJournal.getIssns().stream(), journal.getIssns().stream()).distinct().collect(Collectors.toList());
                    passJournal.setIssns(newIssnList);
                    update = true;
                }

                if (update) {
                    passClient.updateResource(passJournal);
                }
            } else {
                throw new RuntimeException("URI for journal was found, but the object could not be retrieved.");
            }

        }
        return passJournal;
    }
}
