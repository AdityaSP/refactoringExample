package com.wannajob.core.daos.company;

import java.util.List;

import com.wannajob.core.models.CompanyProfile;
import com.wannajob.core.models.CompanyRequest;
import com.wannajob.core.models.JobCategory;
import com.wannajob.core.models.MasterDataRequest;

public class CompanyProfileDAO {

	public void persist(CompanyProfile companyProfile) {
		// TODO Auto-generated method stub
		
	}

	public CompanyProfile find(int companyId) {
		// TODO Auto-generated method stub
		return null;
	}

	public MasterDataRequest getCompanyHirePerYearById(Object hiresPerYearId) {
		// TODO Auto-generated method stub
		return null;
	}

	public MasterDataRequest getCompanySourceById(Object sourceId) {
		// TODO Auto-generated method stub
		return null;
	}

	public MasterDataRequest getCompanySependsRecriterById(Object spendsRecruitingId) {
		// TODO Auto-generated method stub
		return null;
	}

	public MasterDataRequest getCompanyIndustryById(Object industryTypeId) {
		// TODO Auto-generated method stub
		return null;
	}

	public List<JobCategory> getIndustryTypeDetails() {
		// TODO Auto-generated method stub
		return null;
	}

	public List<CompanyRequest> getCompaniesDetails() {
		// TODO Auto-generated method stub
		return null;
	}

	public void updateCompanyLogo(int companyId, byte[] logo, String logoType) {
		// TODO Auto-generated method stub
		
	}

}
