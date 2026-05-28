package com.ecpay.evolution.fixture;

public class HistoricalSalaryRow {
    private String companyId;
    private String document;
    private Integer contractNumber;
    private Long initialDate;
    private Long finalDate;
    private String salaryType;
    private String salaryClassType;
    private Double salary;
    private String coinType;
    private String salaryReason;
    private Integer spendingAccount;

    public String getCompanyId() { return companyId; }
    public String getDocument() { return document; }
    public Integer getContractNumber() { return contractNumber; }
    public Long getInitialDate() { return initialDate; }
    public Long getFinalDate() { return finalDate; }
    public String getSalaryType() { return salaryType; }
    public String getSalaryClassType() { return salaryClassType; }
    public Double getSalary() { return salary; }
    public String getCoinType() { return coinType; }
    public String getSalaryReason() { return salaryReason; }
    public Integer getSpendingAccount() { return spendingAccount; }
}
