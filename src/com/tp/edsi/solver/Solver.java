package com.tp.edsi.solver;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;

import com.tp.edsi.metier.Data;

/**
 * Solver utilis� pour r�soudre le programme lin�aire selon un investissement
 * donn� et un sc�nario donn�.
 * @author Maxime
 */
public class Solver {
	public static final double UNSOLVABLE = -999999999999.99;
	
	/**Solver CPlex*/
	private IloCplex cplex;
	/**Les donn�es charg�es*/
	private Data data;
	
	/**Si il y a un stock initial ou non*/
	private boolean isStockInitial;
	/**Le prix du stock initial*/
	private static final double PRIX_STOCK_INITIAL = 1000;
	
	/**Le nom des fichiers lp correspondant*/
	private String [][] matriceLpFilename;
	/**Les r�sultats correspondant au diff�rent mod�le lp*/
	private double [][] matriceResultats;
	/**Les r�sultats apr�s l'application de l'algorithme moyenne.*/
	private double [] resultatsMoyenne;
	/**Les r�sultats apr�s l'application de l'algorithme MaxMinAbsolu.*/
	private double [] resultatsMaxMinAbsolu;
	/**Les r�sultats apr�s l'application de l'algorithme MaxMinRegret.*/
	private double [] resultatsMaxMinRegret;
	
	public Solver(Data data) throws IloException{
		cplex = new IloCplex();
		
		this.data = data;
		
		//Initialisation des tableaux de r�sultats
		matriceLpFilename = new String [data.getNbInvestissements()][data.getNbScenarios()];
		matriceResultats = new double [data.getNbInvestissements()][data.getNbScenarios()];
		resultatsMoyenne = new double [data.getNbInvestissements()];
		resultatsMaxMinAbsolu = new double [data.getNbScenarios()];
		resultatsMaxMinRegret = new double [data.getNbInvestissements()];

	}
	
	public void setIsStockInitial(boolean isStockInitial){
		this.isStockInitial = isStockInitial;
	}
	
	public boolean isStockInitial(){
		return isStockInitial;
	}
	
	public Data getData(){
		return data;
	}
	
	/**
	 * Algorithme MaxMinAbsolu
	 */
	public void maxMinAbsolu(){
		int nbScenarios = data.getNbScenarios();
		int nbInvestissements = data.getNbInvestissements();
		double solutionMaximale = 0.0;
		
		for(int i = 0; i < nbScenarios; i++){
			for(int j = 0; j < nbInvestissements; j++){
				if(matriceResultats[j][i] > solutionMaximale){
					solutionMaximale = matriceResultats[j][i];
				}
			}
			resultatsMaxMinAbsolu[i] = solutionMaximale;
			solutionMaximale = 0.0;
		}
	}
	
	public double getMaxMinAbsolu(int scenario){
		return resultatsMaxMinAbsolu[scenario];
	}
	
	/**
	 * Algorithme MaxMinRegret
	 */
	public void maxMinRegret(){
		int nbScenarios = data.getNbScenarios();
		int nbInvestissements = data.getNbInvestissements();
		double solutionMaximale = 0.0;
		double[][] resultatRegret = new double[data.getNbInvestissements()][data.getNbScenarios()];
		double minRegret;
		
		for(int i = 0; i < nbInvestissements; i++){
			//Recherche de la solution maximale
			for(int j = 0; j < nbScenarios; j++){
				if(matriceResultats[i][j] > solutionMaximale){
					solutionMaximale = matriceResultats[i][j];
				}
			}
			
			//Cr�ation du tableau max min regret
			for(int j = 0; j < nbScenarios; j++){
				resultatRegret[i][j] = matriceResultats[i][j] - solutionMaximale;
			}
		}
		
		for(int i = 0; i < nbInvestissements; i++){
			minRegret = resultatRegret[i][0];
			for(int j = 0; j < nbScenarios; j++){
				if(resultatRegret[i][j] < minRegret){
					minRegret = matriceResultats[i][j];
				}
			}
			
			resultatsMaxMinRegret[i] = minRegret;
		}
	}
	
	public double getMaxMinRegret(int investissement){
		return resultatsMaxMinRegret[investissement];
	}

	/**
	 * Algorithme moyenne
	 */
	public void moyenne(){
		int nbScenarios = data.getNbScenarios();
		int nbInvestissements = data.getNbInvestissements();
		double somme = 0.0;
		
		for(int i = 0; i < nbInvestissements; i++){
			for(int j = 0; j < nbScenarios; j++){
				somme += matriceResultats[i][j];
			}
			resultatsMoyenne[i] = somme / nbScenarios;
			somme = 0.0;
		}
	}
	
	public double getMoyenne(int investissement){
		return resultatsMoyenne[investissement];
	}
	
	/**R�solution du probl�me : on cr�� un fichier lp pour chaque couple de sc�nario, 
	 * investissement et on r�cup�re le r�sultat.
	 * @throws IloException
	 * @throws IOException
	 */
	public void solveProblem() throws IloException, IOException{
		int nbScenarios = data.getNbScenarios();
		int nbInvestissements = data.getNbInvestissements();
		
		for(int i = 0; i < nbInvestissements; i++){
			for(int j = 0; j < nbScenarios; j++){
				String lpFilename = "solve_" + i + "_" + j + ".lp";
				matriceLpFilename[i][j] = lpFilename;
				solve(lpFilename, i, j);
			}
		}
	}
	
	private void solve(String lpFilename, int investissement, int scenario) throws IloException, IOException{
		createLpFile(lpFilename, investissement, scenario);		
		cplex.importModel(lpFilename);
		
		if(cplex.solve()){
            matriceResultats[investissement][scenario] = cplex.getObjValue();
		}
		else{
			matriceResultats[investissement][scenario] = UNSOLVABLE;
		}
		
	}
	
	public double getSolution(int investissement, int scenario){
		return matriceResultats[investissement][scenario];
	}
	

	
	/**Cr�ation du mod�le LP
	 * @param lpFilename
	 * @param investissement
	 * @param scenario
	 * @throws IOException
	 */
	private void createLpFile(String lpFilename, int investissement, int scenario) throws IOException{
		OutputStream ops = new FileOutputStream(lpFilename); 
		OutputStreamWriter opsw = new OutputStreamWriter(ops);
		BufferedWriter bw = new BufferedWriter(opsw);
		
		bw.write("Maximize\n");
		bw.write("profit: ");
		bw.write(createMaxFunction(investissement, scenario));
		
		bw.write("\n\n");
		bw.write("Subject to\n");
		bw.write(createStockConstraint(scenario));
		bw.write("\n");
		bw.write(createCapaConstraint(investissement));
		
		
		bw.write("\n");
		bw.write("Bounds\n");
		bw.write("cst = 1");
		bw.write("\n");
		
		//STOCK INITIAUX
		if(isStockInitial){
			bw.write("Y_0_0 = 0");
			bw.write("\n");
			bw.write("Y_1_0 = 0");			
		}
		
		bw.write("\n");
		bw.write("End");
		bw.close();
	}
	
	private String createMaxFunction(int investissement, int scenario){
		StringBuilder maxFunction = new StringBuilder();
		
		int vente = 0;
		int achat = 0;
		int ammort = 0;
		
		int nbProduits = data.getNbProduits();
		int nbPeriodes = data.getNbPeriodes();

		StringBuilder production = new StringBuilder();
		StringBuilder stockage = new StringBuilder();
		StringBuilder ammortissement = new StringBuilder();
		
		for(int i = 0; i <= nbPeriodes; i++){
			for(int j = 0; j < nbProduits; j++){
				if(i == nbPeriodes){
					//COUT DE STOCKAGE
					stockage.append("-")
							.append(data.getStockage())
							.append(" Y_").append(j).append("_").append(i).append(" ");
				}
				else{
					//COUT DES VENTES (prix de vente * la demande)
					vente += data.getPrix(j) * data.getPeriode(i).getDemande(j, scenario);

					//COUT DE PRODUCTION
					production.append("-")
							  .append(data.getInvestissement(investissement).getCoutProduction())
							  .append(" X_").append(j).append("_").append(i).append(" ");

					//COUT DE STOCKAGE
					stockage.append("-")
							.append(data.getStockage())
							.append(" Y_").append(j).append("_").append(i).append(" ");
					
					//COUT AMMORTISSEMENT
					ammortissement.append("+")
					  .append(data.getAmortissement())
					  .append(" X_").append(j).append("_").append(i).append(" ");					
				}
			}
		}
		
		//COUT DES ACHATS DE MACHINES
		achat = data.getInvestissement(investissement).getCout();
		ammort = data.getAmortissement() * nbPeriodes * data.getInvestissement(investissement).getCapacite();
		
		//CREATION DE LA FONCTION DE MAXIMISATION
		maxFunction.append(production).append(stockage).append(ammortissement);
		
		if(!isStockInitial){
			maxFunction.append(" - ").append(PRIX_STOCK_INITIAL).append("Y_0_0");
			maxFunction.append(" - ").append(PRIX_STOCK_INITIAL).append("Y_1_0");
		}
				
		maxFunction.append(" +").append(vente - achat - ammort).append(" cst");
		
		
		return maxFunction.toString();
	}
	
	public String createCapaConstraint(int investissement){
		StringBuilder capaConstraint = new StringBuilder();
		
		int nbProduits = data.getNbProduits();
		int nbPerdiodes = data.getNbPeriodes();
		int capacite = data.getInvestissement(investissement).getCapacite();
		
		for(int i = 0; i < nbPerdiodes; i++){
			capaConstraint.append("Capa_").append(i).append(": ");
			for(int j = 0; j < nbProduits; j++){
				capaConstraint.append("X_").append(j).append("_").append(i);
				
				if(j < nbProduits - 1){
					capaConstraint.append(" + ");
				}
			}
			capaConstraint.append(" <= ").append(capacite).append("\n");
		}
		
		return capaConstraint.toString();
	}
	
	public String createStockConstraint(int scenario){
		StringBuilder stockConstraint = new StringBuilder();
		
		int nbProduits = data.getNbProduits();
		int nbPerdiodes = data.getNbPeriodes();
		
		for(int i = 0; i < nbPerdiodes; i++){
			for(int j = 0; j < nbProduits; j++){
				stockConstraint.append("stock_").append(j).append("_").append(i).append(": ")
							   .append("Y_").append(j).append("_").append(i + 1).append(" - ")
							   .append("Y_").append(j).append("_").append(i).append(" - ")
							   .append("X_").append(j).append("_").append(i).append(" = ")
							   .append(" -").append(data.getPeriode(i).getDemande(j, scenario))
							   .append("\n");
			}
		}
		
		return stockConstraint.toString();
	}
}
