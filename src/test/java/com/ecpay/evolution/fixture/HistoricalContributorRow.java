package com.ecpay.evolution.fixture;

public class HistoricalContributorRow {
    private String companyId;
    private String document;
    private Integer contractNumber;
    private Long initialDate;
    private Long finalDate;
    private String contributorType;
    private String subContributorType;
    private Double healthPercentage;
    private Double pensionPercentage;
    private Double solidarityPercentage;
    private Boolean healthAssistance;
    private Boolean pensionAssistance;
    private Boolean pensionForeigner;

    public String getCompanyId() { return companyId; }
    public String getDocument() { return document; }
    public Integer getContractNumber() { return contractNumber; }
    public Long getInitialDate() { return initialDate; }
    public Long getFinalDate() { return finalDate; }
    public String getContributorType() { return contributorType; }
    public String getSubContributorType() { return subContributorType; }
    public Double getHealthPercentage() { return healthPercentage; }
    public Double getPensionPercentage() { return pensionPercentage; }
    public Double getSolidarityPercentage() { return solidarityPercentage; }
    public Boolean getHealthAssistance() { return healthAssistance; }
    public Boolean getPensionAssistance() { return pensionAssistance; }
    public Boolean getPensionForeigner() { return pensionForeigner; }
}
