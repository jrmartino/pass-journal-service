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
package org.dataconservancy.pass.journal.service;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.PassClientFactory;
import org.dataconservancy.pass.client.PassJsonAdapter;
import org.dataconservancy.pass.client.adapter.PassJsonAdapterBasic;
import org.dataconservancy.pass.model.Journal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * @author jrm
 */
public class PassJournalServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(PassJournalServlet.class);

    PassClient passClient = PassClientFactory.getPassClient();
    PassJsonAdapter json = new PassJsonAdapterBasic();


    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        LOG.debug("Servicing new request");

        StringBuffer stringBuffer = new StringBuffer();
        String line;

        BufferedReader reader = request.getReader();
        while ((line = reader.readLine()) != null) {
            stringBuffer.append(line);
        }

        //take the json from crossref and create a Journal
        Journal journal = buildPassJournal(stringBuffer.toString());
        //and compare it with what we already have, update if necessary
        journal = updateJournalInPass(journal);

        if (journal != null) {

            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");

            try (OutputStream out = response.getOutputStream()) {
                out.write(json.toJson(journal, true));
                response.setStatus(200);
            }
        } else {
            try (OutputStream out = response.getOutputStream()) {
                out.write("Input insufficient to specify a journal entry".getBytes());
                response.setStatus(400);
            }
        }
    }

    /**
     * Takes JSON which represents journal article metadata from Crossref
     * and populates a new Journal object. Currently we take typed issns and the journal
     * name.
     * @param jsonInput - the JSON metadata from Crossref
     * @return the PASS journal object
     */
    Journal buildPassJournal(String jsonInput) {

        final String XREF_MESSAGE = "message";
        final String XREF_TITLE = "container-title";
        final String XREF_ISSN_TYPE_ARRAY = "issn-type";
        final String XREF_ISSN_TYPE = "type";
        final String XREF_ISSN_VALUE = "value";


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

            //translate crossref issn-type strings to PASS issn-type strings
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

        passJournal.setId(null); // we don't need this
        return passJournal;
    }

    /**
     * Take a Journal object constructed from Crossref metadata, and compare it with the
     * version of this object which we have in pass. Construct the most complete Journal
     * object possible from the two sources - PASS objects are more authoritative. Use the
     * Crossref version if we don't have it already in PASS. Store the resulting object in PASS.
     * @param journal - the Journal object generated from Crossref metadata
     * @return the updated Journal object, stored in PASS if the PASS object needs updating.
     */
    Journal updateJournalInPass(Journal journal) {
        List<String> issns = journal.getIssns();
        String name = journal.getName();

        Journal passJournal;

        URI passJournalUri = find(name, issns);

        if (passJournalUri == null) {//we don't have this journal in pass yet
            if(name != null && !name.isEmpty() && issns.size()>0) {//we have enough info to make a journal entry
                passJournal = passClient.createAndReadResource(journal, Journal.class);
            } else {//do not have enough to create a new journal
                return null;
            }
        } else { //we have a journal, let's see if we can add anything new - title or issns. we add only if not present
            boolean update = false;
            passJournal = passClient.readResource(passJournalUri, Journal.class);
            if (passJournal != null) {

                //check to see if we can supply a journal name
                if ((passJournal.getName() == null || passJournal.getName().isEmpty()) && (!(journal.getName() == null) && !journal.getName().isEmpty())) {
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
                throw new RuntimeException("URI for journal was found, but the object could not be retrieved. This should never happen.");
            }

        }
        return passJournal;
    }

    /**
     * Find a journal in our repository. We take the best match we can find. finder algorith her eshould harmonize
     * with the approach in the {@code BatchJournalFinder} in the journal loader code
     * @param name the name of the journal to be found
     * @param issns the set of issns to find. we assume that the issns stored in the repo are og the format type:value
     * @return the URI of the best match, or null in nothing matches
     */
     URI find(String name, List<String> issns) {

        Set<URI> nameUriSet = passClient.findAllByAttribute(Journal.class, "name", name);
        Map<URI, Integer> uriScores = new HashMap<>();

        if (!issns.isEmpty()) {
            for (String issn : issns) {
                Set<URI> issnList = passClient.findAllByAttribute(Journal.class, "issns", issn);
                if (issnList != null) {
                    for(URI uri : issnList){
                        Integer i = uriScores.putIfAbsent(uri, 1);
                        if (i != null) {
                            uriScores.put(uri, i + 1);
                        }
                    }
                }
            }
        }


        if (nameUriSet != null) {
            for (URI uri : nameUriSet) {
                Integer i = uriScores.putIfAbsent(uri, 1);
                if (i != null) {
                    uriScores.put(uri, i + 1);
                }
            }
        }


        if(uriScores.size()>0) {//we have matches, pick the best one
            Integer highScore = Collections.max(uriScores.values());
            int minimumQualifyingScore = 1;//with so little to go on, we may realistically get just one hit
            List<URI> sortedUris = new ArrayList<>();

            for (int i = highScore; i >= minimumQualifyingScore; i--) {
                for (URI uri : uriScores.keySet()) {
                    if(uriScores.get(uri) == i) {
                        sortedUris.add(uri);
                    }
                }
            }

            if (sortedUris.size() > 0 ) {// there are matching journals
                return sortedUris.get(0); //return the best match
            }
        } //nothing matches, create a new journal
        return null;
    }

}
