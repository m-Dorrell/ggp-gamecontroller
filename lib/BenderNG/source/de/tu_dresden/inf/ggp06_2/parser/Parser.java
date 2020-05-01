package de.tu_dresden.inf.ggp06_2.parser;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.HashMap;
import org.apache.log4j.Logger;
import de.tu_dresden.inf.ggp06_2.resolver.AndOperator;
import de.tu_dresden.inf.ggp06_2.resolver.Atom;
import de.tu_dresden.inf.ggp06_2.resolver.Const;
import de.tu_dresden.inf.ggp06_2.resolver.DistinctOperator;
import de.tu_dresden.inf.ggp06_2.resolver.DoesPredicate;
import de.tu_dresden.inf.ggp06_2.resolver.Expression;
import de.tu_dresden.inf.ggp06_2.resolver.ExpressionList;
import de.tu_dresden.inf.ggp06_2.resolver.Implication;
import de.tu_dresden.inf.ggp06_2.resolver.InitPredicate;
import de.tu_dresden.inf.ggp06_2.resolver.LegalPredicate;
import de.tu_dresden.inf.ggp06_2.resolver.NotOperator;
import de.tu_dresden.inf.ggp06_2.resolver.OrOperator;
import de.tu_dresden.inf.ggp06_2.resolver.Predicate;
import de.tu_dresden.inf.ggp06_2.resolver.TruePredicate;
import de.tu_dresden.inf.ggp06_2.resolver.structures.Theory;
import de.tu_dresden.inf.ggp06_2.resolver.structures.functions.Function;
import de.tu_dresden.inf.ggp06_2.resolver.structures.relations.Relation;
import java_cup.runtime.lr_parser;

/**
 * This is just a convenience class for all parsers generated by cups.
 * @author Ingo Keller
 *
 */
public class Parser {

    /* Stores the logger for this class */
    public final static Logger logger = Logger.getLogger(Parser.class);

    /* Parser Support */
    static final lr_parser parser = new preKIFParser();

    private static HashMap<Atom, Function> fSymbols;
    private static HashMap<Atom, Relation> rSymbols;
    private static int                     fCount;
    private static int                     rCount;
    private static Atom                    prevSym;
    private static boolean                 isFSym;
    private static boolean                 insideImplication;
    private static boolean                 legalInsideBody;

    private static ExpressionList          lastResult;


    /**
     * Parses a file containing a gdl description into an expression list.
     * @param gdl
     * @return
     */
    public static ExpressionList parseFile( String filename ) {
        isFSym            = false;
        legalInsideBody   = false;
        insideImplication = false;

        try {
            return parseInputStream( new FileInputStream( filename ) );
        }

        catch ( FileNotFoundException e ) {
            logger.error( "File not found: " + filename );
        }
        return new ExpressionList();
    }

    /**
     * Parses a string containing a gdl description into an expression list.
     * @param gdl
     * @return
     */
    public static ExpressionList parseGDL( String gdl ) {
        return parseInputStream( new ByteArrayInputStream( gdl.getBytes() ) );
    }

    /**
     * Parses a input stream into a expression list object.
     * Be aware: This is the real core parsing method.
     * @param stream
     * @return
     */
    private static ExpressionList parseInputStream( InputStream stream ) {
        parser.setScanner( new preKIFScanner( stream ) );
        fSymbols = new HashMap<Atom, Function>();
        rSymbols = new HashMap<Atom, Relation>();
        fCount   = 0;
        rCount   = 0;

        try {
            lastResult = fixList( (ExpressionList) parser.parse().value );
        }
        catch ( Exception e ) {
            logger.error( "Error while parsing: ", e );
            lastResult = new ExpressionList();
        }

        return lastResult;
    }

    /**
     * This method fixes the operator handling.
     *
     * @param expList
     * @return
     * @throws ParseException
     */
    private static ExpressionList fixList (ExpressionList expList)
    throws ParseException {
        ExpressionList newList = new ExpressionList();

        // iterate over every entry
        for ( Expression exp : expList )
            newList.add( fixExpression( exp ) );

        return newList;
    }


    /**
     * This method fixes the operator handling.
     *
     * @param expList
     * @return
     * @throws ParseException
     */
    private static Expression fixExpression (Expression exp)
    throws ParseException {

        // stop for non-predicate stuff
        if ( !(exp instanceof Predicate) )
            return exp;

        // switch to find init and true predicates
        if ( Const.aTrue.equals(prevSym) ||
             Const.aInit.equals(prevSym) ||
             Const.aDoes.equals(prevSym) ||
             Const.aLegal.equals(prevSym) )
            isFSym = true;
        else
            isFSym = false;

        /**
         * Retrieve the basic information.
         */
        Predicate      pred     = (Predicate) exp;
        Atom           operator = pred.getOperator();
        ExpressionList operands = pred.getOperands();
        prevSym = operator;

        if ( insideImplication && Const.aLegal.equals( operator ) )
            legalInsideBody = true;

        /**
         * Predicates without operands are treated as atoms.
         */
        if ( operands.isEmpty() )
            return operator;

        /**
         * Fixes the operator classes
         */
        if ( Const.aAndOp.equals(operator) )
            return new AndOperator(fixList(operands));

        else if ( Const.aOrOp.equals(operator) )
            return new OrOperator(fixList(operands));

        else if ( Const.aDistinctOp.equals(operator) ) {
            if (operands.size() != 2)
                throw new ParseException("Operand number mismatch " + pred, 0);
            return new DistinctOperator( fixExpression(operands.get(0)),
                                         fixExpression(operands.get(1)) );
        }

        else if ( Const.aNotOp.equals(operator) ) {
            if (operands.size() != 1)
                throw new ParseException("Operand number mismatch " + pred, 0);
            return new NotOperator( fixExpression(operands.get(0)) );
        }

        else if ( Const.aImpOp.equals(operator) ) {

            // remove the first element since it is the consequence
            Expression consequence = operands.get(0);
            operands.remove(0);
            Expression fixedCons     = fixExpression(consequence);
            insideImplication        = true;
            ExpressionList fixedList = fixList(operands);
            insideImplication        = false;
            return new Implication( fixedCons, fixedList );
        }

        /**
         * Fixes the predicate classes
         */
        else if ( Const.aInit.equals(operator) ) {
            if (operands.size() != 1)
                throw new ParseException("Operand number mismatch " + pred, 0);
            return new InitPredicate( fixExpression(operands.get(0)) );
        }

        else if ( Const.aTrue.equals(operator) ) {
            if (operands.size() != 1)
                throw new ParseException("Operand number mismatch " + pred, 0);
            return new TruePredicate( fixExpression(operands.get(0)) );
        }

        else if ( Const.aDoes.equals(operator) )
            return new DoesPredicate( fixList(operands) );

        else if ( Const.aLegal.equals(operator) )
            return new LegalPredicate( fixList(operands) );

        // check the normal symbols
        if ( !Const.aImpOp.equals(prevSym) )
            isValidSymbol( operator, operands.size() );

        return new Predicate( operator, operands );
    }

    /**
     * This method checks the type and the arity of a symbol
     * @param symbol
     * @param arity
     * @throws ParseException
     */
    private static void isValidSymbol( Atom symbol, int arity )
    throws ParseException {

        // check for beeing a function or relation symbol
        if ( isFSym ) {

            // check type
            if ( rSymbols.containsKey(symbol) )
                throw new ParseException(symbol + " has multiple types.", 0);

            // add if not existing
            if ( !fSymbols.containsKey(symbol) ) {
                fSymbols.put( symbol, new Function( "" + symbol, arity ) );
                fSymbols.get( symbol ).setId( fCount++ );
            }

            // check if arity fits
            if (fSymbols.get(symbol).arity != arity)
                throw new ParseException(symbol + " has multiple arities.", 0);

        } else {

            // check type
            if ( fSymbols.containsKey(symbol) )
                throw new ParseException(symbol + " has multiple types.", 0);

            // add if not existing
            if ( !rSymbols.containsKey(symbol) ) {
                rSymbols.put( symbol, new Relation( "" + symbol, arity ) );
                rSymbols.get( symbol ).setId( rCount++ );
            }

            // check if arity fits
            if (rSymbols.get(symbol).arity != arity)
                throw new ParseException(symbol + " has multiple arities.", 0);
        }
    }

    public static boolean isLegalInsideBody() {
        return legalInsideBody;
    }

    /**
     * This method returns a fully featured theory object.
     * @return
     */
    public static Theory getFullTheory() {

        Theory tmpTheory = new Theory(lastResult);
        tmpTheory.setFSymbols(fSymbols);
        tmpTheory.setRSymbols(rSymbols);

        // checking the theory
        if ( !tmpTheory.isValid() ) {
            System.err.println("We have a problem - Yeah - Theory is invalid!");
            return null;
        }
        return tmpTheory;
    }

}