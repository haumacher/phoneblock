/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ui;

import java.util.List;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.tabs.Tabs.Orientation;
import com.vaadin.flow.router.Route;

import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReport;

@Route("")
public class MainView extends AppLayout {

	public MainView() {
	    DrawerToggle toggle = new DrawerToggle();

	    H1 title = new H1("PhoneBlock: The spam blocker for your phone");
	    title.getStyle()
	      .set("font-size", "var(--lumo-font-size-l)")
	      .set("margin", "0");

	    VerticalLayout dashboardView = new VerticalLayout();
	    dashboardView.add(new Paragraph("Stop nuisance calls to your landline by adding PhoneBlock as address book to your FritzBox router."));
	    
	    Grid<SpamReport> grid = new Grid<>(SpamReport.class, false);
	    grid.addColumn(SpamReport::getPhone).setHeader("Phone number");
	    grid.addColumn(SpamReport::getVotes).setHeader("Confidence");
	    grid.addColumn(SpamReport::getLastUpdate).setHeader("Time of report");
	    
	    long now = System.currentTimeMillis();
	    List<SpamReport> reports = DBService.getInstance().getLatestSpamReports(now - 60 * 60 * 1000);
	    grid.setItems(reports);		
		setContent(dashboardView);
	    
	    dashboardView.add(grid);
	    
	    Image icon = new Image("app-logo.svg", "");
	    icon.setHeight("1em");
	    icon.getStyle()
	    	.set("vertical-align", "middle")
	    	.set("padding-right", "0.5em");
		H1 appTitle = new H1(icon, new Text("PhoneBlock"));
	    appTitle.getStyle()
	      .set("font-size", "var(--lumo-font-size-l)")
	      .set("line-height", "var(--lumo-size-l)")
	      .set("margin", "0 var(--lumo-space-m)");
	    addToDrawer(appTitle);
	    
	    Tabs tabs = new Tabs();
	    tabs.setOrientation(Orientation.VERTICAL);
	    Tab dashboard = new Tab("Dashboard");
		tabs.add(dashboard);
	    addToDrawer(tabs);
	    
	    addToNavbar(toggle, title);

	    setPrimarySection(Section.DRAWER);
	}
	
}