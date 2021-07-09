package com.wannajob.core.services.company;

import com.wannajob.core.models.CompanyProfile;
import com.wannajob.core.models.MasterDataRequest;

public class CompanyProfileService {

	public CompanyProfile find(int companyId) {
		// TODO Auto-generated method stub
		return null;
	}

	public void update(CompanyProfile companyProfile) {
		// TODO Auto-generated method stub
		
	}

    public MasterDataRequest getCompanyHirePerYearById(Object hiresPerYearId) {
		return  null;
    }

	public MasterDataRequest getCompanySourceById(Object sourceId) {
		return null;
	}

	public MasterDataRequest getCompanySependsRecriterById(Object spendsRecruitingId) {
		return  null;
	}

	public MasterDataRequest getCompanyIndustryById(Object industryTypeId) {
		return null;
	}

	public void persist(CompanyProfile companyProfile) {
	}

	public void updateCompanyLogo(int companyId, byte[] logo, String logoType) {
	}
}
