package com.wannajob.core.services.impl.company;

import java.util.List;

import com.wannajob.core.common.exceptions.WannaJobException;
import com.wannajob.core.models.CompanyRequest;
import com.wannajob.core.models.CompanyUser;
import com.wannajob.core.models.JobCategory;

public interface CompanyService {

	CompanyUser createEmployer(CompanyRequest companyRequest) throws Exception;

	CompanyRequest updateEmployer(CompanyRequest companyRequest, int userId, int companyId) throws WannaJobException;

	CompanyRequest getEmployer(int userId, int companyId);

	List<JobCategory> getIndustryTypeDetails();

	List<CompanyRequest> getCompaniesDetails();

	void updateCompanyLogo(int userId, int companyId, byte[] logo, String logoType) throws WannaJobException;

}
