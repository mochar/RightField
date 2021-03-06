/*******************************************************************************
 * Copyright (c) 2009, 2017, The University of Manchester
 *
 * Licensed under the New BSD License.
 * Please see LICENSE file that is distributed with the source code
 *  
 *******************************************************************************/
package uk.ac.manchester.cs.owl.semspreadsheets.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.semanticweb.owlapi.model.IRI;

import uk.ac.manchester.cs.owl.semspreadsheets.repository.bioportal.LegacyBioportalSourceResolver;

/**
 * Creates and parses a validation sheet, which is a 'very' hidden sheet that contains the data for the named range that forms the
 * dropdown list for the cell, or range of cells, and also contains additional information about the ontology, where it came from, the validation type
 * and the base entity IRI.
 * 
 * @author Matthew Horridge
 * @author Stuart Owen
 */
public class OntologyTermValidationSheetParser {

    public static final String VALIDATION_SHEET_PREFIX = "wksowlv";

    public static final String ONTOLOGY_ROW_KEY = "ontology";

    private WorkbookManager workbookManager;

    private Sheet sheet;
    
    private static final Logger logger = Logger.getLogger(OntologyTermValidationSheetParser.class);

    public OntologyTermValidationSheetParser(WorkbookManager workbookManager,String sheetName) {
    	this(workbookManager,workbookManager.getWorkbook().getSheet(sheetName));
    }
    public OntologyTermValidationSheetParser(WorkbookManager workbookManager, Sheet sheet) {
        this.workbookManager = workbookManager;
        this.sheet = sheet;
    }

    public boolean isValidationSheet() {
        boolean nameMatch = sheet.getName().startsWith(VALIDATION_SHEET_PREFIX);
        return nameMatch && parseValidationType() != null && parseEntityIRI() != null && containsOntologyList();
    }

    private boolean containsOntologyList() {
        Cell cell = sheet.getCellAt(0, 1);
        return cell != null && cell.getValue().trim().equals(ONTOLOGY_ROW_KEY);
    }

    public NamedRange parseNamedRange() {
        Collection<NamedRange> namedRanges = workbookManager.getWorkbook().getNamedRanges();
        for (NamedRange range : namedRanges) {
            Sheet namedRangeSheet = range.getRange().getSheet();
            if (namedRangeSheet != null && namedRangeSheet.getName().equals(sheet.getName())) {
                return range;
            }
        }
        return null;
    }

    private ValidationType parseValidationType() {
        ValidationType type;
        Cell cell = sheet.getCellAt(0, 0);
        if (cell == null) {
            type = ValidationType.FREETEXT;
        }
        else {
            type = ValidationType.valueOf(cell.getValue());
        }
        return type;
    }

    private IRI parseEntityIRI() {
        Cell cell = sheet.getCellAt(1, 0);
        if (cell == null) {
            return null;
        }
        else {
            String iriString = cell.getValue();
            return toIRI(iriString);
        }
    }
    
    private OWLPropertyItem parseOWLProperty() {
    	Cell cell = sheet.getCellAt(3, 0);
    	Cell cell2 = sheet.getCellAt(3, 1);
    	if (cell2==null || cell2.getValue()==null || cell2.getValue().trim().isEmpty()) {
    		return null;
    	}
    	if (cell==null || cell.getValue()==null || cell.getValue().trim().isEmpty()) {
    		return null;
    	}
    	IRI iri = toIRI(cell.getValue());
    	OWLPropertyType type = OWLPropertyType.valueOf(cell2.getValue().trim());
    	return new OWLPropertyItem(iri,type);    	
    }

    public Map<IRI, String> parseTerms() {
        Map<IRI, String> result = new LinkedHashMap<IRI, String>();
        for (int row = 1; ; row++) {
            Cell cell = sheet.getCellAt(0, row);
            if (cell == null) {
                break;
            }
            if (!cell.getValue().equals(ONTOLOGY_ROW_KEY)) {
                IRI termIRI = toIRI(cell.getValue());
                Cell shortNameCell = sheet.getCellAt(1, row);
                String shortName = shortNameCell.getValue();
                result.put(termIRI, shortName);
            }
        }
        return result;
    }

    public Range getTermsShortNameRange() {
        logger.debug("Getting short name range from sheet: " + sheet.getName());
        int startRow = -1;
        int endRow = -1;
        for (int row = 1; ; row++) {
        	logger.debug("Row " + row);
            Cell cell = sheet.getCellAt(0, row);
            if (cell == null) {
                logger.debug("\tCell is null");
                endRow = row - 1;
                break;
            }
            logger.debug("\t" + cell.getValue());
            if (!cell.getValue().equals(ONTOLOGY_ROW_KEY)) {
                if (startRow == -1) {
                    startRow = row;
                }
            }
        }
        Range range = null;
        if (startRow != -1 && endRow != -1) {
            range = new Range(sheet, 1, startRow, 1, endRow);
        }
        return range;
    }

    public OntologyTermValidationDescriptor parseValidationDescriptor() {
        if (!isValidationSheet()) {
            return null;
        }
        return new OntologyTermValidationDescriptor(parseValidationType(), parseEntityIRI(), parseOWLProperty(),parseOntologyIRIs(), parseTerms());
    }

    private Map<IRI, IRI> parseOntologyIRIs() {
        Map<IRI, IRI> result = new HashMap<IRI, IRI>();
        for (int row = 1; ; row++) {
            Cell cell = sheet.getCellAt(0, row);
            if (cell == null) {
                break;
            }
            if (!cell.getValue().trim().equals(ONTOLOGY_ROW_KEY)) {
                break;
            }
            Cell ontologyIRICell = sheet.getCellAt(1, row);
            if (ontologyIRICell != null) {
                IRI ontologyIRI = toIRI(ontologyIRICell.getValue());
                //avoids protege ontology being pulled in from old workbooks
                if (!ontologyIRI.equals(KnownOntologies.PROTEGE_ONTOLOGY_IRI)) {
                	Cell physicalIRICell = sheet.getCellAt(2, row);
                    if (physicalIRICell != null) {
                        IRI physicalIRI = toIRI(physicalIRICell.getValue());                        
                        result.put(ontologyIRI, physicalIRI);
                    }                	
                }                
            }
        }
        return result;
    }

    private IRI toIRI(String iriString) {
        return IRI.create(iriString.substring(1, iriString.length() - 1));
    }

    public void createValidationSheet(OntologyTermValidationDescriptor descriptor) {
        sheet.clearAllCells();
        Workbook workbook = workbookManager.getWorkbook();
        int counter = 0;
        String candidateSheetName = OntologyTermValidationSheetParser.VALIDATION_SHEET_PREFIX + counter;
        while(workbook.containsSheet(candidateSheetName)) {
            counter++;
            candidateSheetName = OntologyTermValidationSheetParser.VALIDATION_SHEET_PREFIX + counter;
        }
        sheet.setName(candidateSheetName);
        setValidationType(descriptor);
        setEntityIRI(descriptor);
        setOntologyList(descriptor);
        setTerms(descriptor);
        setOWLProperty(descriptor);
    }
    
    private void setOWLProperty(OntologyTermValidationDescriptor descriptor) {
    	if (descriptor.getOWLPropertyItem()!=null) {
    		Cell propertyCell = sheet.getCellAt(3, 0);
        	if (propertyCell==null) {
        		propertyCell = sheet.addCellAt(3, 0);
        	}
        	propertyCell.setValue(descriptor.getOWLPropertyItem().getIRI().toQuotedString());
        	
        	Cell propertyTypeCell = sheet.getCellAt(3, 1);
        	if (propertyTypeCell==null) {
        		propertyTypeCell = sheet.addCellAt(3, 1);
        	}
        	propertyTypeCell.setValue(descriptor.getOWLPropertyItem().getPropertyType().toString());
    	}
    	
    }

    private void setValidationType(OntologyTermValidationDescriptor descriptor) {
        Cell typeCell = sheet.getCellAt(0, 0);
        if (typeCell == null) {
            typeCell = sheet.addCellAt(0, 0);
        }
        typeCell.setValue(descriptor.getType().name());

    }

    private void setEntityIRI(OntologyTermValidationDescriptor descriptor) {
        Cell iriCell = sheet.getCellAt(1, 0);
        if (iriCell == null) {
            iriCell = sheet.addCellAt(1, 0);
        }
        iriCell.setValue(descriptor.getEntityIRI().toQuotedString());
    }

    private void setOntologyList(OntologyTermValidationDescriptor descriptor) {
        int row = 1;
        for (IRI iri : descriptor.getOntologyIRIs()) {
            Cell keyCell = sheet.getCellAt(0, row);
            if (keyCell == null) {
                keyCell = sheet.addCellAt(0, row);
            }
            keyCell.setValue(ONTOLOGY_ROW_KEY);
            Cell ontologyIRICell = sheet.getCellAt(1, row);
            if (ontologyIRICell == null) {
                ontologyIRICell = sheet.addCellAt(1, row);
            }
            ontologyIRICell.setValue(iri.toQuotedString());
            Cell physicalIRICell = sheet.getCellAt(2, row);
            if (physicalIRICell == null) {
                physicalIRICell = sheet.addCellAt(2, row);
            }
            IRI physicalIRI = descriptor.getPhysicalIRIForOntologyIRI(iri);
            
            //swap old bioportal IRIs to the new API format, if it matches.
            physicalIRI = LegacyBioportalSourceResolver.getInstance().resolve(physicalIRI);
            physicalIRICell.setValue(physicalIRI.toQuotedString());
            row++;
        }
    }

    private void setTerms(OntologyTermValidationDescriptor descriptor) {
        int row = 1 + descriptor.getOntologyIRIs().size();
        //Collection<Term> terms = descriptor.getTerms(); //AW changed
        Collection<Term> terms = descriptor.getOnlySelectedTerms();    
        logger.debug("There are " + terms.size() + " terms");
        for (Term term : terms) {
        	logger.debug("\t" + term.getName());
            Cell iriCell = sheet.getCellAt(0, row);
            if (iriCell == null) {
                iriCell = sheet.addCellAt(0, row);
            }
            iriCell.setValue(term.getIRI().toQuotedString());
            Cell shortNameCell = sheet.getCellAt(1, row);
            if (shortNameCell == null) {
                shortNameCell = sheet.addCellAt(1, row);
            }
            shortNameCell.setValue(term.getFormattedName());
            row++;
        }

    }


}
