package com.wannajob.core.services.impl.company;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.mysql.cj.protocol.Message;
import com.mysql.cj.protocol.x.MessageConstants;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.refactor.comms.amqp.job.UserNotificationSender;
import com.twilio.exception.AuthenticationException;
import com.wannajob.comms.amqp.otp.OtpGenerator;
import com.wannajob.comms.amqp.otp.OtpSender;
import com.wannajob.core.common.exceptions.WannaJobException;
import com.wannajob.core.common.utility.CommonUtility;
import com.wannajob.core.common.utility.UserCommonUtility;
import com.wannajob.core.daos.company.CompanyProfileDAO;
import com.wannajob.core.daos.company.CompanyUserDAO;
import com.wannajob.core.daos.jobs.JobDAO;
import com.wannajob.core.daos.notifications.UserDeviceTokenDAO;
import com.wannajob.core.daos.users.UserDAO;
import com.wannajob.core.daos.users.UsersRolesDAO;
import com.wannajob.core.models.CompanyProfile;
import com.wannajob.core.models.CompanyRequest;
import com.wannajob.core.models.CompanyUser;
import com.wannajob.core.models.JobCategory;
import com.wannajob.core.models.MasterDataRequest;
import com.wannajob.core.models.User;
import com.wannajob.core.models.UserDeviceToken;
import com.wannajob.core.models.UserNotification;
import com.wannajob.core.models.UsersRoles;
import com.wannajob.core.services.company.CompanyProfileService;
import com.wannajob.core.services.company.CompanyUserService;
import com.wannajob.core.services.users.TokenGeneration;
import com.wannajob.core.services.users.UserService;
import com.wannajob.mongo.models.Cities;
import com.wannajob.mongo.services.CitiesMongoService;

@Service("companyServiceImpl")
@Transactional
public class CompanyServiceImpl implements CompanyService {

	@Autowired
	OtpSender otpSender;

	@Autowired
	OtpGenerator otpGenerator;

	@Autowired
	UserService userService;

	@Autowired
	CompanyProfileService companyProfileService;

	@Autowired
	CompanyUserService companyUserService;

	@Autowired
	CompanyProfileDAO companyProfileDAO;

	@Autowired
	UserDAO userRegisterDAO;

	@Autowired
	CompanyUserDAO companyUserDAO;

	@Autowired
	private UsersRolesDAO usersRolesDAO;

	@Autowired
	CitiesMongoService citiesMongoService;

	@Autowired
	UserDeviceTokenDAO userDeviceTokenDAO;

	@Autowired
	UserNotificationSender userNotificationSender;

	@Autowired
	UserDAO userDAO;

	@Autowired
	JobDAO jobDAO;

	@Autowired
	UserRoleService userRoleService;

	private static final Logger LOGGER = Logger.getLogger(CompanyServiceImpl.class);

	private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@$abcdefghijklmnopqrstuvwxyz";

	public static final String ACTIVE_STRING = "Y";

	public static final String INACTIVE_STRING = "N";

	@Override
	public CompanyUser createEmployer(CompanyRequest companyRequest) throws WannaJobException{

		if (StringUtils.isBlank(companyRequest.getPhoneNo()) || StringUtils.isBlank(companyRequest.getPassword())
				|| StringUtils.isBlank(companyRequest.getPassword()) || StringUtils.isBlank(companyRequest.getName())
				|| StringUtils.isBlank(companyRequest.getName()) || StringUtils.isBlank(companyRequest.getCity())
				|| StringUtils.isBlank(companyRequest.getZipCode())) {
			throw new WannaJobException("Please enter the mandatory details to register!");
		}

		User user = null;
		CompanyUser companyUser = null;

		if (companyRequest.getUserId() == 0 && companyRequest.getCompanyId() == 0) {

			userService.isPhoneNoValid(companyRequest.getPhoneNo());
			User existingUser = userService.getUserByPhoneNo(companyRequest.getPhoneNo());
			if (existingUser != null) {
				throw new WannaJobException(MessageConstant.PHONE_NO_ALREADY_EXITS);
			}

			userService.isPasswordValid(companyRequest.getPassword(), companyRequest.getPassword());
			if (userService.verifyEmail(companyRequest.getEmail())) {
				throw new WannaJobException(MessageConstant.EMAIL_VAL_MSG);
			}

			user = populateUserProfile(companyRequest);
			CompanyProfile companyProfile = populateCompanyProfile(companyRequest);

			userService.persist(user);
			companyProfileService.persist(companyProfile);

			companyUser = new CompanyUser();
			companyUser.setUserId(user.getId());
			companyUser.setCompanyId(companyProfile.getId());
			companyUser.setRoleId(UserCommonUtility.getEmployerTypeId());
			companyUserService.persist(companyUser);

			UsersRoles usersRoles = new UsersRoles();
			usersRoles.setUserId(user.getId());
			usersRoles.setRoleId(2);

			userRoleService.persist(usersRoles);
		} else {
			User exsitingUser = userDAO.find(companyRequest.getUserId());
			CompanyProfile existingCompanyProfile = companyProfileDAO.find(companyRequest.getCompanyId());
			if (exsitingUser == null || existingCompanyProfile == null) {
				throw new WannaJobException(MessageConstant.EMPLOYEE_CREATE_FILED);
			}
			if (exsitingUser.getPhoneNo() == null || !exsitingUser.getPhoneNo()
					.equals(UserCommonUtility.getPhoneNoWithCountryCodePrefix(companyRequest.getPhoneNo()))) {
				User existingUser = userService.getUserByPhoneNo(companyRequest.getPhoneNo());
				if (existingUser != null) {
					throw new WannaJobException(MessageConstant.PHONE_NO_ALREADY_EXITS);
				}
			}
			if (exsitingUser.getEmail() == null || !exsitingUser.getEmail().equals(companyRequest.getEmail())) {
				if (userService.verifyEmail(companyRequest.getEmail())) {
					throw new WannaJobException(MessageConstant.EMAIL_VAL_MSG);
				}
			}

			userService.isPasswordValid(companyRequest.getPassword(), companyRequest.getPassword());

			
			updateCompanyProfile(companyRequest, companyRequest.getCompanyId());
			companyUser = companyUserService
					.getCompanyEmployerUsers(companyRequest.getCompanyId(), UserCommonUtility.getEmployerTypeId()).get(0);
			user = populateUserProfile(companyRequest);
			user.setRoles(exsitingUser.getRoles());
			user.setId(exsitingUser.getId());
			userService.merge(user);
		}

		User newUser = new User();
		try {
			BeanUtils.copyProperties(newUser, user);
		} catch (IllegalAccessException e) {
			//e.printStackTrace();
			LOGGER.error("Error observed during create employee::"+e.getMessage());
		} catch (InvocationTargetException e) {
			//e.printStackTrace();
			LOGGER.error("Error observed during create employee::"+e.getMessage());
		}
		if (companyRequest.getUserDeviceToken() == null || companyRequest.getIsAndroid() == null) {
			LOGGER.error(
					"Create Employer:--> userDeviceToken or isAndroid flag is null or invalid, push notification for OTP is not triggered as userDeviceToken is not saved");
		}

		if (companyRequest.getUserDeviceToken() != null && companyRequest.getIsAndroid() != null) {

			UserDeviceToken userDeviceToken = null;

			List<UserDeviceToken> listToken = userDeviceTokenDAO.getUserDeviceTokens(user.getId());
			if (listToken != null && !listToken.isEmpty()) {
				userDeviceToken = userDeviceTokenDAO.getUserDeviceTokens(user.getId()).get(0);
			}
			if (userDeviceToken == null) {
				userDeviceToken = new UserDeviceToken();
				userDeviceToken.setUserId(newUser.getId());
			}
			userDeviceToken.setDeviceToken(companyRequest.getUserDeviceToken());
			userDeviceToken.setUpdatedDate(CommonUtility.getCurrentDateInUTC());
			userDeviceToken.setIsAndriod(companyRequest.getIsAndroid());
			userDeviceTokenDAO.merge(userDeviceToken);
		}
		newUser.setStatus(UserCommonUtility.getOtpNotificationStatus());
		otpSender.sendOtp(newUser);
		companyUser.setVerificationCode(UserCommonUtility.encodePassword(newUser.getOtp()));

		return companyUser;

	}

	public CompanyUser createEmployerAdminUser(CompanyRequest companyRequest, int adminId) throws WannaJobException {
		String userPassword;
		if (companyRequest.getPhoneNo() != null) {
			userService.isPhoneNoValid(companyRequest.getPhoneNo());
			User existingUser = userService.getUserByPhoneNo(companyRequest.getPhoneNo());
			if (existingUser != null) {
				throw new WannaJobException(MessageConstant.PHONE_NO_ALREADY_EXITS);
			}
		}

		if (userService.verifyEmail(companyRequest.getEmail())) {
			throw new WannaJobException(MessageConstant.EMAIL_VAL_MSG);
		}
		CompanyUser adminCompanyUser = companyUserService.getCompanyUserByUserId(adminId);

		CompanyProfile companyProfile = companyProfileService.find(adminCompanyUser.getCompanyId());

		User user = new User();
		user.setFirstName(companyRequest.getFirstName());
		user.setLastName(companyRequest.getLastName());
		user.setEmail(companyRequest.getEmail());
		userPassword = randomAlphaNumeric(10);
		user.setPassword(UserCommonUtility.encodePassword(userPassword));
		if (companyRequest.getPhoneNo() != null) {
			user.setPhoneNo(UserCommonUtility.getPhoneNoWithCountryCodePrefix(companyRequest.getPhoneNo()));
		}
		user.setUserTypeId(UserCommonUtility.getCompanyUserRoleId());
		user.setAddLine1(companyProfile.getAddLine1());
		user.setAddLine2(companyProfile.getAddLine1());
		user.setCity(companyProfile.getCity());
		user.setState(companyProfile.getState());
		user.setZipCode(companyProfile.getZipCode());
		user.setRegistrationDate(new Date());
		// 1 - user Active. 0 - user Deactivated
		user.setIsActive(User.ACTIVE_USER);

		userRegisterDAO.persist(user);

		CompanyUser companyUser = new CompanyUser();
		companyUser.setUserId(user.getId());
		companyUser.setCompanyId(adminCompanyUser.getCompanyId());
		companyUser.setRoleId(UserCommonUtility.getCompanyUserRoleId());
		companyUserDAO.persist(companyUser);
		companyUser.setUserName(user.getFirstName() + " " + user.getLastName());
		companyUser.setCompanyName(companyProfile.getName());
		LOGGER.info("createEmployerAdminUser for companyId:" + companyUser);

		UsersRoles usersRoles = new UsersRoles();
		usersRoles.setUserId(user.getId());
		usersRoles.setRoleId(UserCommonUtility.getCompanyUserRoleId());
		usersRolesDAO.persist(usersRoles);

		user.setStatus(UserCommonUtility.getPasswordGeneratedStatus());

		// send message, email - user Id and password
		if (userPassword != null && !userPassword.isEmpty()) {
			String[] msgParams = { user.getFirstName() + " " + user.getLastName(), companyProfile.getName(),
					user.getEmail(), userPassword };
			String[] webmsgParams = { companyProfile.getName(), user.getEmail(), userPassword };
			userNotificationSender.sendUserNotification(
					getUserNotification(user, UserCommonUtility.getPasswordGeneratedStatus(), msgParams, webmsgParams));
		}

		return companyUser;
	}

private Object getUserNotification(User user, Object passwordGeneratedStatus, String[] msgParams,
			String[] webmsgParams) {
		// TODO Auto-generated method stub
		return null;
	}

	//	@PreAuthorize("hasRole('ROLE_EMPLOYER')")
	@Override
	public CompanyRequest updateEmployer(CompanyRequest companyRequest, int userId, int companyId)
			throws WannaJobException {

		/*
		 * if (companyRequest.getWebsiteLink() != null) { if
		 * (!UserCommonUtility.isValidURL(companyRequest.getWebsiteLink())) throw new
		 * WannaJobException("Enter a valid website link!"); }
		 */

		User user = userService.find(userId);
		if (user != null) {
			if (user.getUserTypeId() != UserCommonUtility.getCompanyUserRoleId()) {
				updateCompanyProfile(companyRequest, companyId);
			}
			if (!user.getStatus().equals(UserCommonUtility.getPasswordGeneratedStatus())
					&& !user.getStatus().equals(UserCommonUtility.getOtpNotificationStatus())) {
				updateUserProfile(companyRequest, userId);
			} else {
				updateCompanyUserDetails(companyRequest, user);
			}
		}
		CompanyRequest companyDetails = getEmployer(userId, companyId);
		companyDetails.setVerificationCode(UserCommonUtility.encodePassword(user.getOtp()));
		return companyDetails;
	}

	private User populateUserProfile(CompanyRequest companyRequest) throws WannaJobException {

		User user = new User();

		user.setPhoneNo(UserCommonUtility.getPhoneNoWithCountryCodePrefix(companyRequest.getPhoneNo()));
		user.setPassword(UserCommonUtility.encodePassword(companyRequest.getPassword()));
		// user.setPassword(companyRequest.getPassword());
		user.setEmail(companyRequest.getEmail());
		user.setRegistrationDate(new Date());
		user.setUserTypeId(UserCommonUtility.getEmployerTypeId());
		user.setStatus(UserCommonUtility.getOtpGeneratedStatus());
		user.setOtp(otpGenerator.generateOtp());
		user.setAddLine1(companyRequest.getAddLine1());
		user.setAddLine2(companyRequest.getAddLine1());
		user.setZipCode(companyRequest.getZipCode());
		user.setFirstName(companyRequest.getFirstName());
		user.setLastName(companyRequest.getLastName());
		try {
			user.setToken(new TokenGeneration().issueSecureToken(user));
		} catch (AuthenticationException e) {
			e.printStackTrace();
		} 
		return user;
	}

//	@PreAuthorize("hasRole('ROLE_EMPLOYER')")
	@Override
	public CompanyRequest getEmployer(int userId, int companyId) {

		User user = userService.find(userId);
		CompanyProfile companyProfile = companyProfileService.find(companyId);

		CompanyRequest companyRequest = new CompanyRequest();

		MasterDataRequest companyHiresPerYear = null;
		MasterDataRequest companySource = null;
		MasterDataRequest companySpendsRecruiting = null;
		MasterDataRequest companyIndustryType = null;

		if (companyProfile.getHiresPerYearId() != null) {
			companyHiresPerYear = companyProfileService.getCompanyHirePerYearById(companyProfile.getHiresPerYearId());
			companyRequest.setHiresPerYearName(companyHiresPerYear.getName());
			companyRequest.setHiresPerYearId(companyProfile.getHiresPerYearId());
		}

		if (companyProfile.getSourceId() != null) {
			companySource = companyProfileService.getCompanySourceById(companyProfile.getSourceId());
			companyRequest.setSourceName(companySource.getName());
			companyRequest.setSourceId(companyProfile.getSourceId());
		}

		if (companyProfile.getSpendsRecruitingId() != null) {
			companySpendsRecruiting = companyProfileService
					.getCompanySependsRecriterById(companyProfile.getSpendsRecruitingId());
			companyRequest.setSpendsRecruitingName(companySpendsRecruiting.getName());
			companyRequest.setSpendsRecruitingId(companyProfile.getSpendsRecruitingId());
		}

		if (companyProfile.getIndustryTypeId() != null) {
			companyIndustryType = companyProfileService.getCompanyIndustryById(companyProfile.getIndustryTypeId());
			companyRequest.setIndustryTypeName(companyIndustryType.getName());
			companyRequest.setIndustryTypeId(companyProfile.getIndustryTypeId());
		}

		companyRequest.setUserId(user.getId());
		companyRequest.setPhoneNo(user.getPhoneNo());
		companyRequest.setEmail(user.getEmail());
		companyRequest.setTypeId(companyProfile.getTypeId());
		companyRequest.setCompanyId(companyProfile.getId());
		companyRequest.setName(companyProfile.getName());
		companyRequest.setAddress(companyProfile.getAddLine1());
		companyRequest.setAddLine1(companyProfile.getAddLine1());
		companyRequest.setAddLine2(companyProfile.getAddLine1());
		companyRequest.setCity(companyProfile.getCity());
		companyRequest.setState(companyProfile.getState());
		companyRequest.setZipCode(companyProfile.getZipCode());
		companyRequest.setWebsiteLink(companyProfile.getWebsiteLink());
		companyRequest.setFirstName(user.getFirstName());
		companyRequest.setLastName(user.getLastName());
		companyRequest.setIndustryTypeId(companyProfile.getIndustryTypeId());
		companyRequest.setCompanyDescription(companyProfile.getDescription());

		companyRequest.setUserTypeId(user.getUserTypeId());

		companyRequest.setLogo(companyProfile.getLogo());
		companyRequest.setLogoType(companyProfile.getLogoType());

		// companyRequest.setLatitude(latitude);
		List<Cities> cities = citiesMongoService.getLatLongs(companyProfile.getCity(), companyProfile.getState());
		for (Cities city : cities) {
			companyRequest.setLatitude(city.getLatitude());
			companyRequest.setLongitude(city.getLongitude());

		}
		if (user.getIsActive()) {
			companyRequest.setIsEmployerActive(INACTIVE_STRING);
		} else {
			companyRequest.setIsEmployerActive(ACTIVE_STRING);
		}
		return companyRequest;
	}

	private CompanyProfile populateCompanyProfile(CompanyRequest companyRequest) {

		CompanyProfile companyProfile = new CompanyProfile();
		companyProfile.setId(companyRequest.getCompanyId());
		companyProfile.setName(companyRequest.getName());
		companyProfile.setAddLine1(companyRequest.getAddLine1());
		companyProfile.setAddLine2(companyRequest.getAddLine2());
		companyProfile.setState(companyRequest.getState());
		companyProfile.setCity(companyRequest.getCity());
		companyProfile.setZipCode(companyRequest.getZipCode());
		companyProfile.setWebsiteLink(companyRequest.getWebsiteLink());
		companyProfile.setTypeId(UserCommonUtility.getCompanyUserRoleId());
		return companyProfile;
	}

	private CompanyProfile updateCompanyProfile(CompanyRequest companyRequest, int companyId) {
		CompanyProfile companyProfile = new CompanyProfile();
		companyProfile.setId(companyId);
		companyProfile.setWebsiteLink(companyRequest.getWebsiteLink());
		companyProfile.setAddLine1(companyRequest.getAddLine1());
		companyProfile.setAddLine2(companyRequest.getAddLine2());
		companyProfile.setState(companyRequest.getState());
		companyProfile.setCity(companyRequest.getCity());
		companyProfile.setZipCode(companyRequest.getZipCode());
		companyProfile.setSourceId(companyRequest.getSourceId());
		companyProfile.setSpendsRecruitingId(companyRequest.getSpendsRecruitingId());
		companyProfile.setHiresPerYearId(companyRequest.getHiresPerYearId());
		companyProfile.setIndustryTypeId(companyRequest.getIndustryTypeId());
		if (companyRequest.getCompanyDescription() != null) {
			companyProfile.setDescription(companyRequest.getCompanyDescription());
		}
		if (companyRequest.getName() != null) {
			companyProfile.setName(companyRequest.getName());
		}
		companyProfileService.update(companyProfile);
		return companyProfile;
	}

	private void updateUserProfile(CompanyRequest companyRequest, int userId) throws WannaJobException {

		User user = new User();
		User existingUser = null;
		boolean email = false;

		if (StringUtils.isNotBlank(companyRequest.getEmail())) {
			userService.isEmailValid(companyRequest.getEmail());
			existingUser = userService.getUserByContactInfo(null, companyRequest.getEmail());
		} else {
			throw new WannaJobException(MessageConstant.EMAIL_EMPTY);
		}

		if ((existingUser != null && existingUser.getId() == userId)
				|| !userService.verifyEmail(companyRequest.getEmail())) {
			user.setEmail(companyRequest.getEmail());
			user.setAddLine1(companyRequest.getAddLine1());
			user.setAddLine2(companyRequest.getAddLine2());
			user.setZipCode(companyRequest.getZipCode());
			user.setFirstName(companyRequest.getFirstName());
			user.setLastName(companyRequest.getLastName());
			user.setState(companyRequest.getState());
			user.setCity(companyRequest.getCity());
			email = true;
		} else {
			email = false;
		}

		if (email) {
			user.setId(userId);
			userService.update(user);
		} else {
			throw new WannaJobException(MessageConstant.EMAIL_VAL_MSG);
		}

	}

	@Override
	public List<JobCategory> getIndustryTypeDetails() {
		return companyProfileDAO.getIndustryTypeDetails();
	}

	@Override
	public List<CompanyRequest> getCompaniesDetails() {
		return companyProfileDAO.getCompaniesDetails();
	}

	public static String randomAlphaNumeric(int count) {
		StringBuilder builder = new StringBuilder();
		while (count-- != 0) {
			int character = (int) (Math.random() * ALPHA_NUMERIC_STRING.length());
			builder.append(ALPHA_NUMERIC_STRING.charAt(character));
		}
		return builder.toString();
	}

	private UserNotification getUserNotification(User user, String userNotificationStatus, String[] msgParams,
			String[] webmsgParams) {

		UserNotification userNotification = new UserNotification();
		userNotification.setId(user.getId());
		userNotification.setEmail(user.getEmail());
		userNotification.setPhoneNo(user.getPhoneNo());
		userNotification.setStatus(userNotificationStatus);
		userNotification.setMsgParams(msgParams);
		userNotification.setWebMsgParams(webmsgParams);
		return userNotification;
	}

	public List<CompanyRequest> getEmployerAdminUser(int companyId, String activeUserYN, String includeAdminYN,
			int excludeUserId) throws WannaJobException {
		List<CompanyRequest> authUserList = new ArrayList<>();
		List<CompanyRequest> authUserReturnList = new ArrayList<>();

		List<User> userList = userService.getAllCompanyUsers(companyId);
		/*List<CompanyUser> companyUserList = companyUserDAO.getCompanyEmployerUsers(companyId,
				UserCommonUtility.getCompanyUserRoleId());

		if (companyUserList == null || companyUserList.isEmpty()) {
			throw new WannaJobException("No users found");
		}*/


		userList.stream().forEach(user -> {
			CompanyRequest companyRequest = new CompanyRequest();
			companyRequest.setUserId(user.getId());
			companyRequest.setPhoneNo(user.getPhoneNo());
			companyRequest.setEmail(user.getEmail());
			companyRequest.setFirstName(user.getFirstName());
			companyRequest.setLastName(user.getLastName());
			companyRequest.setUserTypeId(user.getUserTypeId());
			companyRequest.setState(user.getName());
			companyRequest.setZipCode(user.getZipCode());
			companyRequest.setAddLine1(user.getAddLine1());
			if (user.getIsActive()) {
				companyRequest.setIsEmployerActive(ACTIVE_STRING);
			} else {
				companyRequest.setIsEmployerActive(INACTIVE_STRING);
			}
//                
			authUserList.add(companyRequest);
			// active jobs count ?

		});

		if (includeAdminYN != null && ACTIVE_STRING.equals(includeAdminYN)) {
			try {
				authUserList.add(getCompanyAdminDetails(companyId));
			} catch (WannaJobException e) {
				LOGGER.error("Failed to retive admin details, company_Id:" + companyId);
			}
		}

		if (ACTIVE_STRING.equals(activeUserYN)) {
			authUserReturnList = authUserList.stream()
					.filter(authUser -> ACTIVE_STRING.equals(authUser.getIsEmployerActive()))
					.collect(Collectors.toList());

		} else if (INACTIVE_STRING.equals(activeUserYN)) {
			authUserReturnList = authUserList.stream()
					.filter(authUser -> INACTIVE_STRING.equals(authUser.getIsEmployerActive()))
					.collect(Collectors.toList());
		} else {
			authUserReturnList = authUserList;
		}

		if (excludeUserId != 0 && !authUserReturnList.isEmpty()) {
			authUserReturnList = authUserReturnList.stream().filter(authUser -> authUser.getUserId() != excludeUserId)
					.collect(Collectors.toList());
		}
		return authUserReturnList;

	}

	private void updateCompanyUserDetails(CompanyRequest companyRequest, User user) throws WannaJobException {
		if (companyRequest != null && user != null) {

			//we can do below validation in controller method before invoking service for updating company user details.

			/*if (StringUtils.isBlank(companyRequest.getPhoneNo()) || StringUtils.isBlank(companyRequest.getPassword())
					|| StringUtils.isBlank(companyRequest.getPassword())) {
				throw new WannaJobException("Please enter the mandatory details to register!");
			}*/

			String phoneNo = UserCommonUtility.getPhoneNoWithCountryCodePrefix(companyRequest.getPhoneNo());

			userService.validatePhoneNumber(companyRequest.getPhoneNo());
			/*userService.isPhoneNoValid(companyRequest.getPhoneNo());
			if (user.getPhoneNo() == null || !user.getPhoneNo().equals(phoneNo)) {
				User existingUser = userService.getUserByPhoneNo(companyRequest.getPhoneNo());
				if (existingUser != null) {
					throw new WannaJobException("This phone number is already registered. Try a new phone number.");
				}
			}*/
			userService.isPasswordValid(companyRequest.getPassword(), companyRequest.getRePassword());

			user.setPassword(UserCommonUtility.encodePassword(companyRequest.getPassword()));
			user.setPhoneNo(phoneNo);

			if (companyRequest.getFirstName() != null) {
				user.setFirstName(companyRequest.getFirstName());
			}
			if (companyRequest.getLastName() != null) {
				user.setLastName(companyRequest.getLastName());
			}
			user.setOtp(otpGenerator.generateOtp());
			user.setStatus(UserCommonUtility.getOtpNotificationStatus());
			userService.update(user);
			//userDAO.merge(user);

			otpSender.sendOtp(user);
		}

	}

	public void deactivateCompanyUser(int userId) throws WannaJobException {
		LOGGER.info("deactivate User- Id " + userId);
		User user = userDAO.find(userId);
		if (!jobDAO.getJobsForEmployer(userId).isEmpty()) {
			throw new WannaJobException(String.format(MessageConstant.USER_DEACTIVE_VAL_MSG,  user.getFirstName(), user.getLastName()));

		}
		userDAO.deactivateUser(userId);
	}

	public CompanyRequest getCompanyAdminDetails(int companyId) throws WannaJobException {

		User user = (User) userService.getAnyCompanyUsers(companyId);

		CompanyRequest companyRequest = new CompanyRequest();

		if (user != null) {
			companyRequest.setUserId(user.getId());
			companyRequest.setPhoneNo(user.getPhoneNo());
			companyRequest.setEmail(user.getEmail());
			companyRequest.setFirstName(user.getFirstName());
			companyRequest.setLastName(user.getLastName());
			companyRequest.setUserTypeId(user.getUserTypeId());
			companyRequest.setState(user.getState());
			companyRequest.setZipCode(user.getZipCode());
			companyRequest.setAddLine1(user.getAddLine1());
			if (user.getIsActive()){
				companyRequest.setIsEmployerActive(ACTIVE_STRING);
			} else {
				companyRequest.setIsEmployerActive(INACTIVE_STRING);
			}

		} else {
			throw new WannaJobException(MessageConstant.USER_NOT_EXITS); //move the message to constant file
		}

		return companyRequest;

	}

	@Override
	public void updateCompanyLogo(int userId, int companyId, byte[] logo, String logoType) throws WannaJobException {

		User existingUser = userService.find(userId);
		CompanyProfile existingCompanyProfile = companyProfileService.find(companyId);
		if (existingUser != null && existingCompanyProfile != null) {
			companyProfileService.updateCompanyLogo(companyId, logo, logoType);
		} else {
			throw new WannaJobException(MessageConstant.USER_NOT_EXITS);
		}
	}

}
