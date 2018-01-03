package org.wikidata.query.rdf.tool.wikibase;

import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.not;
import static org.wikidata.query.rdf.test.CloseableRule.autoClose;
import static org.wikidata.query.rdf.tool.wikibase.WikibaseRepository.inputDateFormat;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.DateUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.openrdf.model.Statement;
import org.wikidata.query.rdf.common.uri.WikibaseUris;
import org.wikidata.query.rdf.test.CloseableRule;
import org.wikidata.query.rdf.tool.change.Change;
import org.wikidata.query.rdf.tool.exception.ContainedException;
import org.wikidata.query.rdf.tool.exception.RetryableException;

import com.carrotsearch.randomizedtesting.RandomizedTest;

/**
 * Tests WikibaseRepository using the beta instance of Wikidata. Note that we
 * can't delete or perform revision deletes so we can't test that part.
 */
public class WikibaseRepositoryIntegrationTest extends RandomizedTest {
    private static final String HOST = "test.wikidata.org";
    @Rule
    public final CloseableRule<WikibaseRepository> repo = autoClose(new WikibaseRepository("https", HOST));
    private final CloseableRule<WikibaseRepository> proxyRepo = autoClose(new WikibaseRepository("http", "localhost", 8812));
    private final WikibaseUris uris = new WikibaseUris(HOST);

    @Test
    @SuppressWarnings("unchecked")
    public void recentChangesWithLotsOfChangesHasContinue() throws RetryableException, ParseException {
        /*
         * This relies on there being lots of changes in the past 30 days. Which
         * is probably ok.
         */
        int batchSize = randomIntBetween(3, 30);
        JSONObject changes = repo.get().fetchRecentChanges(new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)),
                null, batchSize);
        Map<String, Object> c = changes;
        assertThat(c, hasKey("continue"));
        assertThat((Map<String, Object>) changes.get("continue"), hasKey("rccontinue"));
        assertThat(c, hasKey("query"));
        Map<String, Object> query = (Map<String, Object>) c.get("query");
        assertThat(query, hasKey("recentchanges"));
        List<Object> recentChanges = (JSONArray) ((Map<String, Object>) c.get("query")).get("recentchanges");
        assertThat(recentChanges, hasSize(batchSize));
        for (Object rco : recentChanges) {
            Map<String, Object> rc = (Map<String, Object>) rco;
            assertThat(rc, hasEntry(equalTo("ns"), either(equalTo((Object) 0L)).or(equalTo((Object) 120L))));
            assertThat(rc, hasEntry(equalTo("title"), instanceOf(String.class)));
            assertThat(rc, hasEntry(equalTo("timestamp"), instanceOf(String.class)));
            assertThat(rc, hasEntry(equalTo("revid"), instanceOf(Long.class)));
        }
        final Date nextDate = repo.get().getChangeFromContinue((Map<String, Object>)changes.get("continue")).timestamp();
        changes = repo.get().fetchRecentChanges(nextDate, null, batchSize);
        assertThat(c, hasKey("query"));
        assertThat((Map<String, Object>) c.get("query"), hasKey("recentchanges"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void recentChangesWithFewChangesHasNoContinue() throws RetryableException {
        /*
         * This relies on there being very few changes in the current
         * second.
         */
        JSONObject changes = repo.get().fetchRecentChanges(new Date(System.currentTimeMillis()), null, 500);
        Map<String, Object> c = changes;
        assertThat(c, not(hasKey("continue")));
        assertThat(c, hasKey("query"));
        assertThat((Map<String, Object>) c.get("query"), hasKey("recentchanges"));
    }

    @Test
    public void aItemEditShowsUpInRecentChanges() throws RetryableException, ContainedException {
        editShowsUpInRecentChangesTestCase("QueryTestItem", "item");
    }

    @Test
    public void aPropertyEditShowsUpInRecentChanges() throws RetryableException, ContainedException {
        editShowsUpInRecentChangesTestCase("QueryTestProperty", "property");
    }

    private JSONArray getRecentChanges(Date date, int batchSize) throws RetryableException,
        ContainedException {
        // Add a bit of a wait to try and improve Jenkins test stability.
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // nothing to do here, sorry. I know it looks bad.
        }
        JSONObject result = repo.get().fetchRecentChanges(date, null, batchSize);
        return (JSONArray) ((JSONObject) result.get("query")).get("recentchanges");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void editShowsUpInRecentChangesTestCase(String label, String type) throws RetryableException,
            ContainedException {
        long now = System.currentTimeMillis();
        String entityId = repo.get().firstEntityIdForLabelStartingWith(label, "en", type);
        repo.get().setLabel(entityId, type, label + now, "en");
        JSONArray changes = getRecentChanges(new Date(now - 10000), 10);
        boolean found = false;
        String title = entityId;
        if (type.equals("property")) {
            title = "Property:" + title;
        }
        for (Object changeObject : changes) {
            JSONObject change = (JSONObject) changeObject;
            if (change.get("title").equals(title)) {
                found = true;
                Map<String, Object> c = change;
                assertThat(c, hasEntry(equalTo("revid"), isA((Class) Long.class)));
                break;
            }
        }
        assertTrue("Didn't find new page in recent changes", found);
        Collection<Statement> statements = repo.get().fetchRdfForEntity(entityId);
        found = false;
        for (Statement statement : statements) {
            if (statement.getSubject().stringValue().equals(uris.entity() + entityId)) {
                found = true;
                break;
            }
        }
        assertTrue("Didn't find entity information in rdf", found);
    }

    @Test
    public void fetchIsNormalized() throws RetryableException, ContainedException, IOException {
        long now = System.currentTimeMillis();
        try (WikibaseRepository proxyRepo = new WikibaseRepository("http", "localhost", 8812)) {
            String entityId = repo.get().firstEntityIdForLabelStartingWith("QueryTestItem", "en", "item");
            repo.get().setLabel(entityId, "item", "QueryTestItem" + now, "en");
            Collection<Statement> statements = proxyRepo.fetchRdfForEntity(entityId);
            boolean foundBad = false;
            boolean foundGood = false;
            for (Statement statement : statements) {
                if (statement.getObject().stringValue().contains("http://www.wikidata.org/ontology-beta#")) {
                    foundBad = true;
                }
                if (statement.getObject().stringValue().contains("http://www.wikidata.org/ontology-0.0.1#")) {
                    foundBad = true;
                }
                if (statement.getObject().stringValue().contains("http://www.wikidata.org/ontology#")) {
                    foundBad = true;
                }
                if (statement.getObject().stringValue().contains("http://wikiba.se/ontology#")) {
                    foundGood = true;
                }
            }
            assertTrue("Did not find correct ontology statements", foundGood);
            assertFalse("Found incorrect ontology statements", foundBad);
        }
    }

    @Test
    public void continueWorks() throws RetryableException, ContainedException, ParseException, InterruptedException {
        long now = System.currentTimeMillis();
        String entityId = repo.get().firstEntityIdForLabelStartingWith("QueryTestItem", "en", "item");
        repo.get().setLabel(entityId, "item", "QueryTestItem" + now, "en");
        JSONArray changes = getRecentChanges(new Date(now - 10000), 10);
        Change change = null;
        long oldRevid = 0;
        long oldRcid = 0;

        for (Object changeObject : changes) {
            JSONObject rc = (JSONObject) changeObject;
            if (rc.get("title").equals(entityId)) {
                DateFormat df = inputDateFormat();
                Date timestamp = df.parse(rc.get("timestamp").toString());
                oldRevid = (long) rc.get("revid");
                oldRcid = (long)rc.get("rcid");
                change = new Change(rc.get("title").toString(), oldRevid, timestamp, oldRcid);
                break;
            }
        }
        assertNotNull("Did not find the first edit", change);
        // Ensure this change is in different second
        Thread.sleep(1000);
        // make new edit now
        repo.get().setLabel(entityId, "item", "QueryTestItem" + now + "updated", "en");
        changes = getRecentChanges(DateUtils.addSeconds(change.timestamp(), 1), 10);
        // check that new result does not contain old edit but contains new edit
        boolean found = false;
        for (Object changeObject : changes) {
            JSONObject rc = (JSONObject) changeObject;
            if (rc.get("title").equals(entityId)) {
                assertNotEquals("Found old edit after continue: revid", oldRevid, (long) rc.get("revid"));
                assertNotEquals("Found old edit after continue: rcid", oldRcid, (long) rc.get("rcid"));
                found = true;
            }
        }
        assertTrue("Did not find new edit", found);
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void recentChangesWithErrors() throws RetryableException, ContainedException {
        JSONObject changes = proxyRepo.get().fetchRecentChanges(new Date(System.currentTimeMillis()), null, 500);
        Map<String, Object> c = changes;
        assertThat(c, not(hasKey("continue")));
        assertThat(c, hasKey("query"));
        assertThat((Map<String, Object>) c.get("query"), hasKey("recentchanges"));
    }

    // TODO we should verify the RDF dump format against a stored file
}
