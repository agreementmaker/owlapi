/* This file is part of the OWL API.
 * The contents of this file are subject to the LGPL License, Version 3.0.
 * Copyright 2014, The University of Manchester
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0 in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. */
package uk.ac.manchester.cs.owl.owlapi;

import static org.semanticweb.owlapi.util.CollectionFactory.*;
import static org.semanticweb.owlapi.util.OWLAPIPreconditions.*;
import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.*;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

import org.semanticweb.owlapi.OWLAPIConfigProvider;
import org.semanticweb.owlapi.io.IRIDocumentSource;
import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.io.OWLOntologyDocumentTarget;
import org.semanticweb.owlapi.io.OWLOntologyStorageIOException;
import org.semanticweb.owlapi.io.OWLParserFactory;
import org.semanticweb.owlapi.io.OntologyIRIMappingNotFoundException;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.AddOntologyAnnotation;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.DefaultChangeBroadcastStrategy;
import org.semanticweb.owlapi.model.DefaultImpendingChangeBroadcastStrategy;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.ImmutableOWLOntologyChangeException;
import org.semanticweb.owlapi.model.ImpendingOWLOntologyChangeBroadcastStrategy;
import org.semanticweb.owlapi.model.ImpendingOWLOntologyChangeListener;
import org.semanticweb.owlapi.model.MissingImportEvent;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.MissingImportListener;
import org.semanticweb.owlapi.model.OWLAnnotationAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLMutableOntology;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyAlreadyExistsException;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyChangeBroadcastStrategy;
import org.semanticweb.owlapi.model.OWLOntologyChangeListener;
import org.semanticweb.owlapi.model.OWLOntologyChangeProgressListener;
import org.semanticweb.owlapi.model.OWLOntologyChangeVetoException;
import org.semanticweb.owlapi.model.OWLOntologyChangesVetoedListener;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyDocumentAlreadyExistsException;
import org.semanticweb.owlapi.model.OWLOntologyFactory;
import org.semanticweb.owlapi.model.OWLOntologyFactoryNotFoundException;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyLoaderListener;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyRenameException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLRuntimeException;
import org.semanticweb.owlapi.model.OWLStorerFactory;
import org.semanticweb.owlapi.model.OWLStorerNotFoundException;
import org.semanticweb.owlapi.model.PriorityCollectionSorting;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.model.RemoveImport;
import org.semanticweb.owlapi.model.SetOntologyID;
import org.semanticweb.owlapi.model.UnknownOWLOntologyException;
import org.semanticweb.owlapi.model.UnloadableImportException;
import org.semanticweb.owlapi.model.parameters.ChangeApplied;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;
import org.semanticweb.owlapi.util.PriorityCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Provider;

/**
 * @author Matthew Horridge, The University Of Manchester, Bio-Health
 *         Informatics Group
 * @since 2.0.0
 */
public class OWLOntologyManagerImpl implements OWLOntologyManager,
        OWLOntologyFactory.OWLOntologyCreationHandler, Serializable {

    private static final String BADLISTENER = "BADLY BEHAVING LISTENER: {} has been removed";
    private static final long serialVersionUID = 40000L;
    private static final Logger LOGGER = LoggerFactory
            .getLogger(OWLOntologyManagerImpl.class);
    @Nonnull
    protected final Map<OWLOntologyID, OWLOntology> ontologiesByID = createSyncMap();
    @Nonnull
    protected final Map<OWLOntologyID, IRI> documentIRIsByID = createSyncMap();
    @Nonnull
    protected final Map<OWLOntologyID, OWLOntologyLoaderConfiguration> ontologyConfigurationsByOntologyID = createSyncMap();
    @Nonnull
    protected final Map<OWLOntologyID, OWLDocumentFormat> ontologyFormatsByOntology = createSyncMap();
    @Nonnull
    protected final Map<OWLImportsDeclaration, OWLOntologyID> ontologyIDsByImportsDeclaration = createSyncMap();
    @Nonnull
    protected final PriorityCollection<OWLOntologyIRIMapper> documentMappers = new PriorityCollection<>(
        PriorityCollectionSorting.ON_SET_INJECTION_ONLY);
    @Nonnull
    protected final PriorityCollection<OWLOntologyFactory> ontologyFactories = new PriorityCollection<>(
        PriorityCollectionSorting.ON_SET_INJECTION_ONLY);
    @Nonnull
    protected final PriorityCollection<OWLParserFactory> parserFactories = new PriorityCollection<>(
        PriorityCollectionSorting.ON_SET_INJECTION_ONLY);
    @Nonnull
    protected final PriorityCollection<OWLStorerFactory> ontologyStorers = new PriorityCollection<>(
        PriorityCollectionSorting.ON_SET_INJECTION_ONLY);
    private final AtomicBoolean broadcastChanges = new AtomicBoolean(true);
    protected final AtomicInteger loadCount = new AtomicInteger(0);
    protected final AtomicInteger importsLoadCount = new AtomicInteger(0);
    @Nonnull
    protected final Set<IRI> importedIRIs = createSyncSet();
    @Nonnull
    protected final OWLDataFactory dataFactory;
    @Nonnull
    protected final Map<OWLOntologyID, Set<OWLOntology>> importsClosureCache = createSyncMap();
    @Nonnull
    protected final List<MissingImportListener> missingImportsListeners = createSyncList();
    @Nonnull
    protected final List<OWLOntologyLoaderListener> loaderListeners = createSyncList();
    @Nonnull
    protected final List<OWLOntologyChangeProgressListener> progressListeners = createSyncList();
    @Nonnull
    protected final AtomicLong autoGeneratedURICounter = new AtomicLong();
    @Nonnull
    protected OWLOntologyChangeBroadcastStrategy defaultChangeBroadcastStrategy = new DefaultChangeBroadcastStrategy();
    @Nonnull
    protected ImpendingOWLOntologyChangeBroadcastStrategy defaultImpendingChangeBroadcastStrategy = new DefaultImpendingChangeBroadcastStrategy();
    private transient Map<OWLOntologyChangeListener, OWLOntologyChangeBroadcastStrategy> listenerMap = createSyncMap();
    private transient Map<ImpendingOWLOntologyChangeListener, ImpendingOWLOntologyChangeBroadcastStrategy> impendingChangeListenerMap = createSyncMap();
    private transient List<OWLOntologyChangesVetoedListener> vetoListeners = new ArrayList<>();
    @Nonnull
    private Provider<OWLOntologyLoaderConfiguration> configProvider = new OWLAPIConfigProvider();
    @Nonnull
    private transient Optional<OWLOntologyLoaderConfiguration> config = Optional
            .empty();

    /**
     * @param dataFactory
     *        data factory
     */
    public OWLOntologyManagerImpl(@Nonnull OWLDataFactory dataFactory) {
        this.dataFactory = checkNotNull(dataFactory,
                "dataFactory cannot be null");
        installDefaultURIMappers();
        installDefaultOntologyFactories();
    }

    @Override
    public void setOntologyLoaderConfigurationProvider(
            Provider<OWLOntologyLoaderConfiguration> provider) {
        configProvider = provider;
    }

    @Override
    public void setOntologyLoaderConfiguration(
            OWLOntologyLoaderConfiguration newConfig) {
        config = Optional.ofNullable(newConfig);
    }

    @Override
    @Nonnull
    public OWLOntologyLoaderConfiguration getOntologyLoaderConfiguration() {
        if (config.isPresent()) {
            return config.get();
        }
        config = Optional.of(configProvider.get());
        return config.get();
    }

    @Override
    public OWLDataFactory getOWLDataFactory() {
        return dataFactory;
    }

    @Override
    public Stream<OWLOntology> ontologies() {
        return ontologiesByID.values().stream();
    }

    @Override
    public boolean contains(OWLOntology ontology) {
        return ontologiesByID.containsValue(ontology);
    }

    @Override
    public boolean contains(IRI iri) {
        checkNotNull(iri, "Ontology IRI cannot be null");
        return ids().anyMatch(o -> o.match(iri));
        // FIXME: ParsableOWLOntologyFactory seems to call this method with a
        // document/physical IRI,
        // but this method fails the general case where the ontology was loaded
        // from the given IRI directly, but was then renamed
    }

    /** @return stream of ids */
    protected Stream<OWLOntologyID> ids() {
        return ontologiesByID.keySet().stream();
    }

    @Override
    public boolean contains(OWLOntologyID id) {
        if (id.isAnonymous()) {
            return false;
        }
        if (ontologiesByID.containsKey(id)) {
            return true;
        }
        return ids().anyMatch(o -> id.match(o));
    }

    @Override
    public boolean containsVersion(IRI iri) {
        return ids().anyMatch(o -> o.matchVersion(iri));
    }

    @Override
    public Stream<OWLOntologyID> ontologyIDsByVersion(IRI iri) {
        return ids().filter(o -> o.matchVersion(iri));
    }

    @Override
    public OWLOntology getOntology(IRI iri) {
        OWLOntologyID ontologyID = new OWLOntologyID(of(iri), of((IRI) null));
        OWLOntology result = ontologiesByID.get(ontologyID);
        if (result != null) {
            return result;
        }
        java.util.Optional<Entry<OWLOntologyID, OWLOntology>> findAny = ontologiesByID
                .entrySet().stream().filter(o -> o.getKey().match(iri))
                .findAny();
        return findAny.isPresent() ? findAny.get().getValue() : null;
    }

    @Override
    public OWLOntology getOntology(OWLOntologyID id) {
        OWLOntology result = ontologiesByID.get(id);
        if (result == null && !id.isAnonymous()) {
            java.util.Optional<OWLOntologyID> findAny = ids().filter(
                    o -> o.matchOntology(id.getOntologyIRI().get())).findAny();
            if (findAny.isPresent()) {
                result = ontologiesByID.get(findAny.get());
            }
        }
        // HACK: This extra clause is necessary to make getOntology match the
        // behaviour of createOntology in cases where a documentIRI has been
        // recorded, based on the mappers, but an ontology has not been stored
        // in ontologiesByID
        if (result == null) {
            IRI documentIRI = getDocumentIRIFromMappers(id);
            if (documentIRI == null) {
                if (!id.isAnonymous()) {
                    documentIRI = id.getDefaultDocumentIRI().orElse(null);
                } else {
                    documentIRI = IRI.generateDocumentIRI();
                }
                Collection<IRI> existingDocumentIRIs = documentIRIsByID
                        .values();
                while (existingDocumentIRIs.contains(documentIRI)) {
                    documentIRI = IRI.generateDocumentIRI();
                }
            }
            if (documentIRIsByID.values().contains(documentIRI)) {
                throw new OWLRuntimeException(
                        new OWLOntologyDocumentAlreadyExistsException(
                                documentIRI));
            }
        }
        return result;
    }

    @Nullable
    @Override
    public OWLOntology getImportedOntology(OWLImportsDeclaration declaration) {
        OWLOntologyID ontologyID = ontologyIDsByImportsDeclaration
                .get(declaration);
        if (ontologyID == null) {
            // No such ontology has been loaded through an import declaration,
            // but it might have been loaded manually.
            // Using the IRI to retrieve it will either find the ontology or
            // return null
            return getOntology(declaration.getIRI());
        } else {
            return getOntology(ontologyID);
        }
    }

    @Override
    public Stream<OWLOntology> directImports(OWLOntology ontology) {
        if (!contains(ontology)) {
            throw new UnknownOWLOntologyException(ontology.getOntologyID());
        }
        return ontology.importsDeclarations()
                .map(ax -> getImportedOntology(ax)).filter(o -> o != null);
    }

    @Override
    public Stream<OWLOntology> imports(OWLOntology ontology) {
        return getImports(ontology, new LinkedHashSet<>()).stream();
    }

    /**
     * A method that gets the imports of a given ontology.
     * 
     * @param ont
     *        The ontology whose (transitive) imports are to be retrieved.
     * @param result
     *        A place to store the result - the transitive closure of the
     *        imports will be stored in this result set.
     * @return modified result
     */
    private Set<OWLOntology> getImports(@Nonnull OWLOntology ont,
            @Nonnull Set<OWLOntology> result) {
        directImports(ont).filter(o -> result.add(o)).forEach(
                o -> getImports(o, result));
        return result;
    }

    @Override
    public Stream<OWLOntology> importsClosure(OWLOntology ontology) {
        OWLOntologyID id = ontology.getOntologyID();
        return importsClosureCache.computeIfAbsent(id,
                i -> getImportsClosure(ontology, new LinkedHashSet<>()))
                .stream();
    }

    /**
     * A recursive method that gets the reflexive transitive closure of the
     * ontologies that are imported by this ontology.
     * 
     * @param ontology
     *        The ontology whose reflexive transitive closure is to be retrieved
     * @param ontologies
     *        a place to store the result
     * @return modified ontologies
     */
    private Set<OWLOntology> getImportsClosure(@Nonnull OWLOntology ontology,
            @Nonnull Set<OWLOntology> ontologies) {
        ontologies.add(ontology);
        directImports(ontology).filter(o -> !ontologies.contains(o)).forEach(
                o -> getImportsClosure(o, ontologies));
        return ontologies;
    }

    @Override
    public List<OWLOntology> getSortedImportsClosure(OWLOntology ontology) {
        return asList(ontology.importsClosure().sorted());
    }

    /**
     * Determines if a change is applicable. A change may not be applicable for
     * a number of reasons.
     * 
     * @param change
     *        The change to be tested.
     * @return {@code true} if the change is applicable, otherwise,
     *         {@code false}.
     */
    private boolean isChangeApplicable(OWLOntologyChange change) {
        OWLOntologyLoaderConfiguration ontologyConfig = ontologyConfigurationsByOntologyID
                .get(change.getOntology().getOntologyID());
        if (ontologyConfig != null && !ontologyConfig.isLoadAnnotationAxioms()
                && change.isAddAxiom()
                && change.getAxiom() instanceof OWLAnnotationAxiom) {
            return false;
        }
        return true;
    }

    /**
     * Applies a change to an ontology and performs the necessary housekeeping
     * tasks.
     * 
     * @param change
     *        The change to be applied.
     * @return A list of changes that were actually applied.
     */
    private List<OWLOntologyChange> enactChangeApplication(
            OWLOntologyChange change) {
        if (!isChangeApplicable(change)) {
            return Collections.emptyList();
        }
        OWLOntology ont = change.getOntology();
        if (!(ont instanceof OWLMutableOntology)) {
            throw new ImmutableOWLOntologyChangeException(
                    change.getChangeData(), ont.toString());
        }
        checkForOntologyIDChange(change);
        ChangeApplied appliedChange = ((OWLMutableOntology) ont)
                .applyDirectChange(change);
        checkForImportsChange(change);
        if (appliedChange == ChangeApplied.UNSUCCESSFULLY) {
            return Collections.emptyList();
        }
        return Collections.singletonList(change);
    }

    @Override
    public List<OWLOntologyChange> applyChanges(
            List<? extends OWLOntologyChange> changes) {
        try {
            broadcastImpendingChanges(changes);
        } catch (OWLOntologyChangeVetoException e) {
            // Some listener blocked the changes.
            broadcastOntologyChangesVetoed(changes, e);
            return Collections.emptyList();
        }
        List<OWLOntologyChange> appliedChanges = new ArrayList<>(
                changes.size() + 2);
        fireBeginChanges(changes.size());
        for (OWLOntologyChange change : changes) {
            appliedChanges.addAll(enactChangeApplication(change));
            fireChangeApplied(change);
        }
        fireEndChanges();
        broadcastChanges(appliedChanges);
        return appliedChanges;
    }

    @Override
    public ChangeApplied addAxiom(@Nonnull OWLOntology ont,
            @Nonnull OWLAxiom axiom) {
        List<OWLOntologyChange> addAxioms = addAxioms(ont, createSet(axiom));
        if (addAxioms.isEmpty()) {
            return ChangeApplied.UNSUCCESSFULLY;
        }
        return ChangeApplied.SUCCESSFULLY;
    }

    @Override
    public List<OWLOntologyChange> addAxioms(@Nonnull OWLOntology ont,
            @Nonnull Stream<? extends OWLAxiom> axioms) {
        List<AddAxiom> changes = asList(axioms.map(ax -> new AddAxiom(ont, ax)));
        return applyChanges(changes);
    }

    @Override
    public ChangeApplied removeAxiom(@Nonnull OWLOntology ont,
            @Nonnull OWLAxiom axiom) {
        List<OWLOntologyChange> removeAxioms = removeAxioms(ont,
                createSet(axiom));
        if (removeAxioms.isEmpty()) {
            return ChangeApplied.UNSUCCESSFULLY;
        }
        return ChangeApplied.SUCCESSFULLY;
    }

    @Override
    public List<OWLOntologyChange> removeAxioms(@Nonnull OWLOntology ont,
            @Nonnull Collection<? extends OWLAxiom> axioms) {
        List<RemoveAxiom> changes = new ArrayList<>(axioms.size() + 2);
        axioms.forEach(ax -> changes.add(new RemoveAxiom(ont, ax)));
        return applyChanges(changes);
    }

    @Override
    public ChangeApplied applyChange(@Nonnull OWLOntologyChange change) {
        List<OWLOntologyChange> applyChanges = applyChanges(list(change));
        if (applyChanges.isEmpty()) {
            return ChangeApplied.UNSUCCESSFULLY;
        }
        return ChangeApplied.SUCCESSFULLY;
    }

    private void checkForImportsChange(OWLOntologyChange change) {
        if (change.isImportChange()) {
            resetImportsClosureCache();
            if (change instanceof AddImport) {
                OWLImportsDeclaration addImportDeclaration = ((AddImport) change)
                        .getImportDeclaration();
                IRI iri = addImportDeclaration.getIRI();
                java.util.Optional<OWLOntologyID> findFirst = ids().filter(
                        o -> o.match(iri) || o.matchDocument(iri)).findFirst();
                findFirst.ifPresent(o -> ontologyIDsByImportsDeclaration.put(
                        addImportDeclaration, o));
                if (!findFirst.isPresent()) {
                    // then the import does not refer to a known IRI for
                    // ontologies; check for a document IRI to find the ontology
                    // id corresponding to the file location
                    documentIRIsByID
                            .entrySet()
                            .stream()
                            .filter(o -> o.getValue().equals(iri))
                            .findAny()
                            .ifPresent(
                                    o -> ontologyIDsByImportsDeclaration.put(
                                            addImportDeclaration, o.getKey()));
                }
            } else {
                // Remove the mapping from declaration to ontology
                OWLImportsDeclaration importDeclaration = ((RemoveImport) change)
                        .getImportDeclaration();
                ontologyIDsByImportsDeclaration.remove(importDeclaration);
                importedIRIs.remove(importDeclaration.getIRI());
            }
        }
    }

    private void checkForOntologyIDChange(OWLOntologyChange change) {
        if (!(change instanceof SetOntologyID)) {
            return;
        }
        SetOntologyID setID = (SetOntologyID) change;
        OWLOntology existingOntology = ontologiesByID.get(setID
                .getNewOntologyID());
        OWLOntology o = setID.getOntology();
        if (existingOntology != null && !o.equals(existingOntology)) {
            if (!asSet(o.axioms()).equals(asSet(existingOntology.axioms()))) {
                LOGGER.error(
                        "OWLOntologyManagerImpl.checkForOntologyIDChange() existing:{}",
                        existingOntology);
                LOGGER.error(
                        "OWLOntologyManagerImpl.checkForOntologyIDChange() new:{}",
                        o);
                throw new OWLOntologyRenameException(setID.getChangeData(),
                        setID.getNewOntologyID());
            }
        }
        renameOntology(setID.getOriginalOntologyID(), setID.getNewOntologyID());
        resetImportsClosureCache();
    }

    // Methods to create, load and reload ontologies
    @Override
    public void ontologyCreated(OWLOntology ontology) {
        // This method is called when a factory that we have asked to create or
        // load an ontology has created the ontology. We add the ontology to the
        // set of loaded ontologies.
        addOntology(ontology);
    }

    @Override
    public void setOntologyFormat(OWLOntology ontology,
            OWLDocumentFormat ontologyFormat) {
        OWLOntologyID ontologyID = ontology.getOntologyID();
        ontologyFormatsByOntology.put(ontologyID, ontologyFormat);
    }

    @Nonnull
    @Override
    public OWLDocumentFormat getOntologyFormat(@Nonnull OWLOntology ontology) {
        OWLOntologyID ontologyID = ontology.getOntologyID();
        return ontologyFormatsByOntology.get(ontologyID);
    }

    @Nonnull
    @Override
    public OWLOntology createOntology(@Nonnull OWLOntologyID ontologyID)
            throws OWLOntologyCreationException {
        OWLOntology ontology = ontologiesByID.get(ontologyID);
        if (ontology != null) {
            throw new OWLOntologyAlreadyExistsException(ontologyID);
        }
        IRI documentIRI = getDocumentIRIFromMappers(ontologyID);
        if (documentIRI == null) {
            if (!ontologyID.isAnonymous()) {
                documentIRI = ontologyID.getDefaultDocumentIRI().orElse(null);
            } else {
                documentIRI = IRI.generateDocumentIRI();
            }
            Collection<IRI> existingDocumentIRIs = documentIRIsByID.values();
            while (existingDocumentIRIs.contains(documentIRI)) {
                documentIRI = IRI.generateDocumentIRI();
            }
        }
        if (documentIRIsByID.values().contains(documentIRI)) {
            throw new OWLOntologyDocumentAlreadyExistsException(documentIRI);
        }
        for (OWLOntologyFactory factory : ontologyFactories) {
            if (factory.canCreateFromDocumentIRI(documentIRI)) {
                documentIRIsByID.put(ontologyID, documentIRI);
                return factory.createOWLOntology(this, ontologyID, documentIRI,
                        this);
            }
        }
        throw new OWLOntologyFactoryNotFoundException(documentIRI);
    }

    @Override
    public OWLOntology createOntology(IRI ontologyIRI,
            Stream<OWLOntology> ontologies, boolean copyLogicalAxiomsOnly)
            throws OWLOntologyCreationException {
        if (contains(ontologyIRI)) {
            throw new OWLOntologyAlreadyExistsException(new OWLOntologyID(
                    of(ontologyIRI), Optional.empty()));
        }
        OWLOntology ont = createOntology(ontologyIRI);
        Function<? super OWLOntology, ? extends Stream<? extends OWLAxiom>> mapper = o -> copyLogicalAxiomsOnly ? o
                .logicalAxioms() : o.axioms();
        addAxioms(ont, asSet(ontologies.flatMap(mapper)));
        return ont;
    }

    @Override
    public OWLOntology createOntology(Stream<OWLAxiom> axioms, IRI ontologyIRI)
            throws OWLOntologyCreationException {
        if (contains(ontologyIRI)) {
            throw new OWLOntologyAlreadyExistsException(new OWLOntologyID(
                    of(ontologyIRI), Optional.empty()));
        }
        OWLOntology ont = createOntology(ontologyIRI);
        addAxioms(ont, axioms);
        return ont;
    }

    @Override
    public OWLOntology copyOntology(@Nonnull OWLOntology toCopy,
            @Nonnull OntologyCopy settings) throws OWLOntologyCreationException {
        checkNotNull(toCopy);
        checkNotNull(settings);
        OWLOntology toReturn = null;
        switch (settings) {
            case MOVE:
                toReturn = toCopy;
                ontologiesByID.put(toReturn.getOntologyID(), toReturn);
                break;
            case SHALLOW:
            case DEEP:
                OWLOntology o = createOntology(toCopy.getOntologyID());
                AxiomType.AXIOM_TYPES.forEach(t -> addAxioms(o,
                        toCopy.getAxioms(t)));
                toCopy.annotations().forEach(
                        a -> applyChange(new AddOntologyAnnotation(o, a)));
                toReturn = o;
                break;
            default:
                throw new OWLRuntimeException("settings value not understood: "
                        + settings);
        }
        // toReturn now initialized
        OWLOntologyManager m = toCopy.getOWLOntologyManager();
        if (settings == OntologyCopy.MOVE || settings == OntologyCopy.DEEP) {
            setOntologyDocumentIRI(toReturn, m.getOntologyDocumentIRI(toCopy));
            OWLDocumentFormat f = m.getOntologyFormat(toCopy);
            if (f != null) {
                setOntologyFormat(toReturn, f);
            }
        }
        if (settings == OntologyCopy.MOVE) {
            m.removeOntology(toCopy);
            // at this point toReturn and toCopy are the same object
            // change the manager on the ontology
            toReturn.setOWLOntologyManager(this);
        }
        return toReturn;
    }

    @Override
    public OWLOntology loadOntology(IRI ontologyIRI)
            throws OWLOntologyCreationException {
        return loadOntology(ontologyIRI, false,
                getOntologyLoaderConfiguration());
    }

    @Nonnull
    protected OWLOntology loadOntology(@Nonnull IRI iri, boolean allowExists,
            @Nonnull OWLOntologyLoaderConfiguration configuration)
            throws OWLOntologyCreationException {
        OWLOntology ontByID = null;
        // Check for matches on the ontology IRI first
        java.util.Optional<OWLOntologyID> findAny = ids().filter(
                o -> o.match(iri)).findAny();
        if (findAny.isPresent()) {
            ontByID = ontologiesByID.get(findAny.get());
        }
        if (ontByID != null) {
            return ontByID;
        }
        OWLOntologyID id = new OWLOntologyID(of(iri), Optional.empty());
        IRI documentIRI = getDocumentIRIFromMappers(id);
        if (documentIRI != null) {
            if (documentIRIsByID.values().contains(documentIRI) && !allowExists) {
                throw new OWLOntologyDocumentAlreadyExistsException(documentIRI);
            }
            // The ontology might be being loaded, but its IRI might
            // not have been set (as is probably the case with RDF/XML!)
            OWLOntology ontByDocumentIRI = getOntologyByDocumentIRI(documentIRI);
            if (ontByDocumentIRI != null) {
                return ontByDocumentIRI;
            }
        } else {
            // Nothing we can do here. We can't get a document IRI to load
            // the ontology from.
            throw new OntologyIRIMappingNotFoundException(iri);
        }
        if (documentIRIsByID.values().contains(documentIRI) && !allowExists) {
            throw new OWLOntologyDocumentAlreadyExistsException(documentIRI);
        }
        // The ontology might be being loaded, but its IRI might
        // not have been set (as is probably the case with RDF/XML!)
        OWLOntology ontByDocumentIRI = getOntologyByDocumentIRI(documentIRI);
        if (ontByDocumentIRI != null) {
            return ontByDocumentIRI;
        }
        return loadOntology(iri,
                new IRIDocumentSource(documentIRI, null, null), configuration);
    }

    private OWLOntology getOntologyByDocumentIRI(IRI iri) {
        java.util.Optional<Entry<OWLOntologyID, IRI>> findAny = documentIRIsByID
                .entrySet().stream().filter(o -> iri.equals(o.getValue()))
                .findAny();
        if (findAny.isPresent()) {
            return getOntology(findAny.get().getKey());
        }
        return null;
    }

    @Override
    public OWLOntology loadOntologyFromOntologyDocument(
            OWLOntologyDocumentSource documentSource,
            OWLOntologyLoaderConfiguration conf)
            throws OWLOntologyCreationException {
        return loadOntology(null, documentSource, conf);
    }

    /**
     * This is the method that all the other load method delegate to.
     * 
     * @param ontologyIRI
     *        The URI of the ontology to be loaded. This is only used to report
     *        to listeners and may be {@code null}
     * @param documentSource
     *        The input source that specifies where the ontology should be
     *        loaded from.
     * @param configuration
     *        load configuration
     * @return The ontology that was loaded.
     * @throws OWLOntologyCreationException
     *         If the ontology could not be loaded.
     */
    @Nonnull
    protected OWLOntology loadOntology(@Nullable IRI ontologyIRI,
            @Nonnull OWLOntologyDocumentSource documentSource,
            @Nonnull OWLOntologyLoaderConfiguration configuration)
            throws OWLOntologyCreationException {
        if (loadCount.get() != importsLoadCount.get()) {
            LOGGER.error("Runtime Warning: Parsers should load imported ontologies using the makeImportLoadRequest method.");
        }
        fireStartedLoadingEvent(
                new OWLOntologyID(of(ontologyIRI), Optional.empty()),
                documentSource.getDocumentIRI(), loadCount.get() > 0);
        loadCount.incrementAndGet();
        broadcastChanges.set(false);
        Exception ex = null;
        OWLOntologyID idOfLoadedOntology = new OWLOntologyID();
        try {
            for (OWLOntologyFactory factory : ontologyFactories) {
                if (factory.canAttemptLoading(documentSource)) {
                    try {
                        // Note - there is no need to add the ontology here,
                        // because it will be added
                        // when the ontology is created.
                        OWLOntology ontology = factory.loadOWLOntology(this,
                                documentSource, this, configuration);
                        idOfLoadedOntology = ontology.getOntologyID();
                        // Store the ontology to the document IRI mapping
                        documentIRIsByID.put(ontology.getOntologyID(),
                                documentSource.getDocumentIRI());
                        ontologyConfigurationsByOntologyID.put(
                                ontology.getOntologyID(), configuration);
                        if (ontology instanceof HasTrimToSize) {
                            ((HasTrimToSize) ontology).trimToSize();
                        }
                        return ontology;
                    } catch (OWLOntologyRenameException e) {
                        // We loaded an ontology from a document and the
                        // ontology turned out to have an IRI the same
                        // as a previously loaded ontology
                        throw new OWLOntologyAlreadyExistsException(
                                e.getOntologyID(), e);
                    }
                }
            }
        } catch (UnloadableImportException | OWLOntologyCreationException e) {
            ex = e;
            throw e;
        } catch (OWLRuntimeException e) {
            if (e.getCause() instanceof OWLOntologyCreationException) {
                ex = (OWLOntologyCreationException) e.getCause();
                throw (OWLOntologyCreationException) e.getCause();
            }
            throw e;
        } finally {
            if (loadCount.decrementAndGet() == 0) {
                broadcastChanges.set(true);
                // Completed loading ontology and imports
            }
            fireFinishedLoadingEvent(idOfLoadedOntology,
                    documentSource.getDocumentIRI(), loadCount.get() > 0, ex);
        }
        throw new OWLOntologyFactoryNotFoundException(
                documentSource.getDocumentIRI());
    }

    @Override
    public void removeOntology(OWLOntologyID ontologyID) {
        OWLOntology o = ontologiesByID.remove(ontologyID);
        ontologyFormatsByOntology.remove(ontologyID);
        documentIRIsByID.remove(ontologyID);
        if (o != null) {
            o.setOWLOntologyManager(null);
            resetImportsClosureCache();
        }
    }

    private void addOntology(OWLOntology ont) {
        ontologiesByID.put(ont.getOntologyID(), ont);
        resetImportsClosureCache();
    }

    @Override
    public IRI getOntologyDocumentIRI(OWLOntology ontology) {
        if (!contains(ontology)) {
            throw new UnknownOWLOntologyException(ontology.getOntologyID());
        }
        return verifyNotNull(documentIRIsByID.get(ontology.getOntologyID()));
    }

    @Override
    public void setOntologyDocumentIRI(OWLOntology ontology, IRI documentIRI) {
        if (!ontologiesByID.containsKey(ontology.getOntologyID())) {
            throw new UnknownOWLOntologyException(ontology.getOntologyID());
        }
        documentIRIsByID.put(ontology.getOntologyID(), documentIRI);
    }

    /**
     * Handles a rename of an ontology. This method should only be called
     * *after* the change has been applied
     * 
     * @param oldID
     *        The original ID of the ontology
     * @param newID
     *        The new ID of the ontology
     */
    private void renameOntology(OWLOntologyID oldID, OWLOntologyID newID) {
        OWLOntology ont = ontologiesByID.get(oldID);
        if (ont == null) {
            // Nothing to rename!
            return;
        }
        ontologiesByID.remove(oldID);
        ontologiesByID.put(newID, ont);
        if (ontologyFormatsByOntology.containsKey(oldID)) {
            ontologyFormatsByOntology.put(newID,
                    ontologyFormatsByOntology.remove(oldID));
        }
        IRI documentIRI = documentIRIsByID.remove(oldID);
        if (documentIRI != null) {
            documentIRIsByID.put(newID, documentIRI);
        }
        resetImportsClosureCache();
    }

    protected void resetImportsClosureCache() {
        importsClosureCache.clear();
    }

    @Override
    public void saveOntology(OWLOntology ontology,
            OWLDocumentFormat ontologyFormat, IRI documentIRI)
            throws OWLOntologyStorageException {
        try {
            for (OWLStorerFactory storer : ontologyStorers) {
                if (storer.getFormatFactory().getKey()
                        .equals(ontologyFormat.getKey())) {
                    storer.get().storeOntology(ontology, documentIRI,
                            ontologyFormat);
                    return;
                }
            }
            throw new OWLStorerNotFoundException(ontologyFormat);
        } catch (IOException e) {
            throw new OWLOntologyStorageIOException(e);
        }
    }

    @Override
    public void saveOntology(OWLOntology ontology,
            OWLDocumentFormat ontologyFormat,
            OWLOntologyDocumentTarget documentTarget)
            throws OWLOntologyStorageException {
        String formatKey = ontologyFormat.getKey();
        try {
            for (OWLStorerFactory storer : ontologyStorers) {
                String key = storer.getFormatFactory().getKey();
                if (key.equals(formatKey)) {
                    storer.get().storeOntology(ontology, documentTarget,
                            ontologyFormat);
                    return;
                }
            }
            throw new OWLStorerNotFoundException(ontologyFormat);
        } catch (IOException e) {
            throw new OWLOntologyStorageIOException(e);
        }
    }

    @Override
    @Inject
    public void setOntologyStorers(Set<OWLStorerFactory> storers) {
        ontologyStorers.set(storers);
    }

    @Override
    public PriorityCollection<OWLStorerFactory> getOntologyStorers() {
        return ontologyStorers;
    }

    @Override
    @Inject
    public void setIRIMappers(Set<OWLOntologyIRIMapper> mappers) {
        documentMappers.set(mappers);
    }

    @Override
    public PriorityCollection<OWLOntologyIRIMapper> getIRIMappers() {
        return documentMappers;
    }

    @Override
    public void addIRIMapper(OWLOntologyIRIMapper mapper) {
        documentMappers.add(mapper);
    }

    @Override
    public void removeIRIMapper(OWLOntologyIRIMapper mapper) {
        documentMappers.remove(mapper);
    }

    @Override
    public void clearIRIMappers() {
        documentMappers.clear();
    }

    @Override
    public void addOntologyStorer(OWLStorerFactory storer) {
        ontologyStorers.add(storer);
    }

    @Override
    public void removeOntologyStorer(OWLStorerFactory storer) {
        ontologyStorers.remove(storer);
    }

    @Override
    public void clearOntologyStorers() {
        ontologyStorers.clear();
    }

    @Override
    @Inject
    public void setOntologyFactories(Set<OWLOntologyFactory> factories) {
        ontologyFactories.set(factories);
    }

    @Override
    @Inject
    public void setOntologyParsers(Set<OWLParserFactory> parsers) {
        parserFactories.set(parsers);
    }

    @Override
    public PriorityCollection<OWLParserFactory> getOntologyParsers() {
        return parserFactories;
    }

    @Override
    public PriorityCollection<OWLOntologyFactory> getOntologyFactories() {
        return ontologyFactories;
    }

    /**
     * Uses the mapper mechanism to obtain an ontology document IRI from an
     * ontology IRI.
     * 
     * @param ontologyID
     *        The ontology ID for which a document IRI is to be retrieved
     * @param quiet
     *        If set to {@code true} and a mapping can't be found then a value
     *        of {@code null} is returned. If set to {@code false} and a mapping
     *        can't be found then an exception
     *        {@link org.semanticweb.owlapi.model.OWLOntologyIRIMappingNotFoundException}
     *        is thrown.
     * @return The document IRI that corresponds to the ontology IRI, or
     *         {@code null} if no physical URI can be found.
     */
    @Nullable
    private IRI getDocumentIRIFromMappers(OWLOntologyID ontologyID) {
        Optional<IRI> defIRI = ontologyID.getDefaultDocumentIRI();
        if (!defIRI.isPresent()) {
            return null;
        }
        for (OWLOntologyIRIMapper mapper : documentMappers) {
            IRI documentIRI = mapper
                    .getDocumentIRI(defIRI.get());
            if (documentIRI != null) {
                return documentIRI;
            }
        }
        return defIRI.get();
    }

    protected final void installDefaultURIMappers() {    }

    protected final void installDefaultOntologyFactories() {
        // The default factories are the ones that can load
        // ontologies from http:// and file:// URIs
    }

    // Listener stuff - methods to add/remove listeners
    private void readObject(java.io.ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        config = Optional.ofNullable((OWLOntologyLoaderConfiguration) stream
                .readObject());
        listenerMap = new ConcurrentHashMap<>();
        impendingChangeListenerMap = new ConcurrentHashMap<>();
        vetoListeners = new ArrayList<>();
    }

    private void writeObject(ObjectOutputStream stream) throws IOException {
        stream.defaultWriteObject();
        stream.writeObject(config.orElse(null));
    }

    @Override
    public void addOntologyChangeListener(OWLOntologyChangeListener listener) {
        listenerMap.put(listener, defaultChangeBroadcastStrategy);
    }

    /**
     * Broadcasts to attached listeners, using the various broadcasting
     * strategies that were specified for each listener.
     * 
     * @param changes
     *        The ontology changes to broadcast
     */
    protected void broadcastChanges(
            @Nonnull List<? extends OWLOntologyChange> changes) {
        if (!broadcastChanges.get()) {
            return;
        }
        for (OWLOntologyChangeListener listener : new ArrayList<>(
                listenerMap.keySet())) {
            OWLOntologyChangeBroadcastStrategy strategy = listenerMap
                    .get(listener);
            if (strategy == null) {
                // This listener may have been removed during the broadcast of
                // the changes, so when we attempt to retrieve it from the map
                // it isn't there (because we iterate over a copy).
                continue;
            }
            try {
                // Handle exceptions on a per listener basis. If we have
                // badly behaving listeners, we don't want one listener
                // to prevent the other listeners from receiving events.
                strategy.broadcastChanges(listener, changes);
            } catch (Exception e) {
                LOGGER.warn(BADLISTENER, e.getMessage(), e);
                listenerMap.remove(listener);
            }
        }
    }

    protected void broadcastImpendingChanges(
            @Nonnull List<? extends OWLOntologyChange> changes) {
        if (!broadcastChanges.get()) {
            return;
        }
        for (ImpendingOWLOntologyChangeListener listener : new ArrayList<>(
                impendingChangeListenerMap.keySet())) {
            ImpendingOWLOntologyChangeBroadcastStrategy strategy = impendingChangeListenerMap
                    .get(listener);
            if (strategy != null) {
                strategy.broadcastChanges(listener, changes);
            }
        }
    }

    @Override
    public void setDefaultChangeBroadcastStrategy(
            OWLOntologyChangeBroadcastStrategy strategy) {
        defaultChangeBroadcastStrategy = strategy;
    }

    @Override
    public void addOntologyChangeListener(OWLOntologyChangeListener listener,
            OWLOntologyChangeBroadcastStrategy strategy) {
        listenerMap.put(listener, strategy);
    }

    @Override
    public void addImpendingOntologyChangeListener(
            ImpendingOWLOntologyChangeListener listener) {
        impendingChangeListenerMap.put(listener,
                defaultImpendingChangeBroadcastStrategy);
    }

    @Override
    public void removeImpendingOntologyChangeListener(
            ImpendingOWLOntologyChangeListener listener) {
        impendingChangeListenerMap.remove(listener);
    }

    @Override
    public void
            removeOntologyChangeListener(OWLOntologyChangeListener listener) {
        listenerMap.remove(listener);
    }

    @Override
    public void addOntologyChangesVetoedListener(
            OWLOntologyChangesVetoedListener listener) {
        vetoListeners.add(listener);
    }

    @Override
    public void removeOntologyChangesVetoedListener(
            OWLOntologyChangesVetoedListener listener) {
        vetoListeners.remove(listener);
    }

    private void broadcastOntologyChangesVetoed(
            @Nonnull List<? extends OWLOntologyChange> changes,
            @Nonnull OWLOntologyChangeVetoException veto) {
        new ArrayList<>(vetoListeners).forEach(l -> l.ontologyChangesVetoed(
                changes, veto));
    }

    // Imports etc.
    protected OWLOntology loadImports(OWLImportsDeclaration declaration,
            @Nonnull OWLOntologyLoaderConfiguration configuration)
            throws OWLOntologyCreationException {
        importsLoadCount.incrementAndGet();
        OWLOntology ont = null;
        try {
            ont = loadOntology(declaration.getIRI(), true, configuration);
        } catch (OWLOntologyCreationException e) {
            if (configuration.getMissingImportHandlingStrategy() == MissingImportHandlingStrategy.THROW_EXCEPTION) {
                throw e;
            } else {
                // Silent
                MissingImportEvent evt = new MissingImportEvent(
                        declaration.getIRI(), e);
                fireMissingImportEvent(evt);
            }
        } finally {
            importsLoadCount.decrementAndGet();
        }
        return ont;
    }

    @Override
    public void makeLoadImportRequest(OWLImportsDeclaration declaration,
            OWLOntologyLoaderConfiguration configuration) {
        IRI iri = declaration.getIRI();
        if (!configuration.isIgnoredImport(iri) && !importedIRIs.contains(iri)) {
            importedIRIs.add(iri);
            try {
                OWLOntology ont = loadImports(declaration, configuration);
                if (ont != null) {
                    ontologyIDsByImportsDeclaration.put(declaration,
                            ont.getOntologyID());
                }
            } catch (OWLOntologyCreationException e) {
                // Wrap as UnloadableImportException and throw
                throw new UnloadableImportException(e, declaration);
            }
        }
    }

    @Override
    public void addMissingImportListener(MissingImportListener listener) {
        missingImportsListeners.add(listener);
    }

    @Override
    public void removeMissingImportListener(
            @Nonnull MissingImportListener listener) {
        missingImportsListeners.remove(listener);
    }

    protected void fireMissingImportEvent(@Nonnull MissingImportEvent evt) {
        new ArrayList<>(missingImportsListeners).forEach(l -> l
                .importMissing(evt));
    }

    // Other listeners etc.
    @Override
    public void addOntologyLoaderListener(OWLOntologyLoaderListener listener) {
        loaderListeners.add(listener);
    }

    @Override
    public void
            removeOntologyLoaderListener(OWLOntologyLoaderListener listener) {
        loaderListeners.remove(listener);
    }

    protected void fireStartedLoadingEvent(OWLOntologyID ontologyID,
            IRI documentIRI, boolean imported) {
        for (OWLOntologyLoaderListener listener : new ArrayList<>(
                loaderListeners)) {
            listener.startedLoadingOntology(new OWLOntologyLoaderListener.LoadingStartedEvent(
                    ontologyID, documentIRI, imported));
        }
    }

    protected void fireFinishedLoadingEvent(OWLOntologyID ontologyID,
            IRI documentIRI, boolean imported, Exception ex) {
        for (OWLOntologyLoaderListener listener : new ArrayList<>(
                loaderListeners)) {
            listener.finishedLoadingOntology(new OWLOntologyLoaderListener.LoadingFinishedEvent(
                    ontologyID, documentIRI, imported, ex));
        }
    }

    @Override
    public void addOntologyChangeProgessListener(
            OWLOntologyChangeProgressListener listener) {
        progressListeners.add(listener);
    }

    @Override
    public void removeOntologyChangeProgessListener(
            OWLOntologyChangeProgressListener listener) {
        progressListeners.remove(listener);
    }

    protected void fireBeginChanges(int size) {
        if (!broadcastChanges.get()) {
            return;
        }
        for (OWLOntologyChangeProgressListener listener : progressListeners) {
            try {
                listener.begin(size);
            } catch (Exception e) {
                LOGGER.warn(BADLISTENER, e.getMessage(), e);
                progressListeners.remove(listener);
            }
        }
    }

    protected void fireEndChanges() {
        if (!broadcastChanges.get()) {
            return;
        }
        for (OWLOntologyChangeProgressListener listener : progressListeners) {
            try {
                listener.end();
            } catch (Exception e) {
                LOGGER.warn(BADLISTENER, e.getMessage(), e);
                progressListeners.remove(listener);
            }
        }
    }

    protected void fireChangeApplied(@Nonnull OWLOntologyChange change) {
        if (!broadcastChanges.get()) {
            return;
        }
        if (progressListeners.isEmpty()) {
            return;
        }
        for (OWLOntologyChangeProgressListener listener : progressListeners) {
            try {
                listener.appliedChange(change);
            } catch (Exception e) {
                LOGGER.warn(BADLISTENER, e.getMessage(), e);
                progressListeners.remove(listener);
            }
        }
    }

    @Nonnull
    protected <T> Optional<T> of(T t) {
        return Optional.ofNullable(t);
    }
}
