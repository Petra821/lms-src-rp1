package jp.co.sss.lms.controller;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.service.StudentAttendanceService;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.MessageUtil;

/**
 * 勤怠管理コントローラ
 * 
 * @author 東京ITスクール
 */
@Controller
@RequestMapping("/attendance")
public class AttendanceController {

	@Autowired
	private StudentAttendanceService studentAttendanceService;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private MessageUtil messageUtil;

	/**
	 * 勤怠管理画面 初期表示
	 * 
	 * @param lmsUserId
	 * @param courseId
	 * @param model
	 * @return 勤怠管理画面
	 * @throws ParseException
	 */
	@RequestMapping(path = "/detail", method = RequestMethod.GET)
	public String index(Model model) {

		// 勤怠一覧の取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);

		// 未入力件数チェック
		// ログインユーザーのIDを取得し、Serviceに渡している
		// DTOにはユーザーから入力された値やセッション情報が一時的に格納されているためここから取得する
		boolean notEnterAttendance = studentAttendanceService.notEnterAttendance(loginUserDto.getLmsUserId());
		model.addAttribute("notEnterAttendance", notEnterAttendance);

		return "attendance/detail";
	}

	/**
	 * 勤怠管理画面 『出勤』ボタン押下
	 * 
	 * @param model
	 * @return 勤怠管理画面
	 */
	@RequestMapping(path = "/detail", params = "punchIn", method = RequestMethod.POST)
	public String punchIn(Model model) {

		// 更新前のチェック
		String error = studentAttendanceService.punchCheck(Constants.CODE_VAL_ATWORK);
		model.addAttribute("error", error);
		// 勤怠登録
		if (error == null) {
			String message = studentAttendanceService.setPunchIn();
			model.addAttribute("message", message);
		}
		// 一覧の再取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);

		return "attendance/detail";
	}

	/**
	 * 勤怠管理画面 『退勤』ボタン押下
	 * 
	 * @param model
	 * @return 勤怠管理画面
	 */
	@RequestMapping(path = "/detail", params = "punchOut", method = RequestMethod.POST)
	public String punchOut(Model model) {

		// 更新前のチェック
		String error = studentAttendanceService.punchCheck(Constants.CODE_VAL_LEAVING);
		model.addAttribute("error", error);
		// 勤怠登録
		if (error == null) {
			String message = studentAttendanceService.setPunchOut();
			model.addAttribute("message", message);
		}
		// 一覧の再取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);

		return "attendance/detail";
	}

	/**
	 * 勤怠管理画面 『勤怠情報を直接編集する』リンク押下
	 * 
	 * @param model
	 * @return 勤怠情報直接変更画面
	 */
	@RequestMapping(path = "/update")
	public String update(Model model) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		// 勤怠フォームの生成
		AttendanceForm attendanceForm = studentAttendanceService
				.setAttendanceForm(attendanceManagementDtoList);
		model.addAttribute("attendanceForm", attendanceForm);

		// エラー表示用の配列を初期化
		boolean[] startHourError = new boolean[attendanceForm.getAttendanceList().size()];
		boolean[] startMinuteError = new boolean[attendanceForm.getAttendanceList().size()];
		boolean[] endHourError = new boolean[attendanceForm.getAttendanceList().size()];
		boolean[] endMinuteError = new boolean[attendanceForm.getAttendanceList().size()];
		
		model.addAttribute("startHourError", startHourError);
		model.addAttribute("startMinuteError", startMinuteError);
		model.addAttribute("endHourError", endHourError);
		model.addAttribute("endMinuteError", endMinuteError);

		return "attendance/update";
	}

	/**
	 * 勤怠情報直接変更画面 『更新』ボタン押下
	 * 
	 * @param attendanceForm
	 * @param model
	 * @param result
	 * @return 勤怠管理画面
	 * @throws ParseException
	 */
	@RequestMapping(path = "/update", params = "complete", method = RequestMethod.POST)
	public String complete(AttendanceForm attendanceForm, Model model, BindingResult result)
			throws ParseException {

		// 入力チェック用
		boolean hasError = false;
		boolean[] startHourError = new boolean[attendanceForm.getAttendanceList().size()];
		boolean[] startMinuteError = new boolean[attendanceForm.getAttendanceList().size()];
		boolean[] endHourError = new boolean[attendanceForm.getAttendanceList().size()];
		boolean[] endMinuteError = new boolean[attendanceForm.getAttendanceList().size()];
		
		// AttendanceForm.getAttendanceList()をループ
		// 各項目ごとに条件をチェックし、エラーがあればエラーメッセージリストに追加
		// エラーが一つでもあれば、attendance/update画面にエラー付きで返す
		List<String> errorList = new ArrayList<>();
		for (int i = 0; i < attendanceForm.getAttendanceList().size(); i++) {
			DailyAttendanceForm daily = attendanceForm.getAttendanceList().get(i);

			// a. 備考文字数
			if (daily.getNote() != null && daily.getNote().length() > 100) {
				errorList.add(messageUtil.getMessage("maxlength", new String[]{"備考", "100"}));
			}

			// b. 出勤時間が一部未入力（時間と分の片方のみ入力）
			boolean startHour = daily.getTrainingStartHour() != null;
			boolean startMinute = daily.getTrainingStartMinute() != null;
			if ((startHour && !startMinute) || (!startHour && startMinute)) {
				if (!startHour) {
					startHourError[i] = true;
				}
				if (!startMinute) {
					startMinuteError[i] = true;
				}
				errorList.add(messageUtil.getMessage("input.invalid", new String[]{"出勤時間"}));
				hasError = true;
			}
			
			// c. 退勤時間が一部未入力（時間と分の片方のみ入力）
			boolean endHour = daily.getTrainingEndHour() != null;
			boolean endMinute = daily.getTrainingEndMinute() != null;
			if ((endHour && !endMinute) || (!endHour && endMinute)) {
				if (!endHour) {
					endHourError[i] = true;
				}
				if (!endMinute) {
					endMinuteError[i] = true;
				}
				errorList.add(messageUtil.getMessage("input.invalid", new String[]{"退勤時間"}));
				hasError = true;
			}
			// d. 退勤時間のみ入力（出勤時間が未入力の状態で退勤時間を入力）
			if (!startHour && !startMinute && (endHour || endMinute)) {
				startHourError[i] = true;
				startMinuteError[i] = true;
				errorList.add(messageUtil.getMessage("attendance.punchInEmpty"));
				hasError = true;
			}
			// e. 出勤時間 > 退勤時間（退勤時間より出勤時間の方が遅い）単位を分にそろえて比較
			if (startHour && startMinute && endHour && endMinute) {
				int start = daily.getTrainingStartHour() * 60 + daily.getTrainingStartMinute();
				int end = daily.getTrainingEndHour() * 60 + daily.getTrainingEndMinute();
				if (start >= end) {
					startHourError[i] = true;
					startMinuteError[i] = true;
					endHourError[i] = true;
					endMinuteError[i] = true;
					errorList.add(messageUtil.getMessage("attendance.trainingTimeRange", new String[]{String.valueOf(i + 1)}));
					hasError = true;
				}
				// f. 中抜け時間が勤務時間を超える（単位をそろえた変数が使えるスコープ内でやると良い）
				if (daily.getBlankTime() != null && (daily.getBlankTime() > (end - start))) {
					errorList.add(messageUtil.getMessage("attendance.blankTimeError"));
					hasError = true;
				}
			}
			
			// メモ
			// new Object[]としていたが、getMessageメソッドの引数型が(String,String[])になっているためnew String[]とした
			// これに伴い、e.の部分でint型のiを引数に入れられなくなったため、String.valueOf()を使用した

			// HTMLも追記が必要（CSSを生かした仕組みにしたい）
			// この条件分岐がどこから情報を持ってきて何と比較しているのか、あとですべて追う
			
		}

		// エラーがある場合は入力画面に戻る
		if (hasError) {
			model.addAttribute("attendanceForm", attendanceForm);
			model.addAttribute("startHourError", startHourError);
			model.addAttribute("startMinuteError", startMinuteError);
			model.addAttribute("endHourError", endHourError);
			model.addAttribute("endMinuteError", endMinuteError);
			model.addAttribute("errorList", errorList);
			return "attendance/update";
		}

		
		// 更新
		String message = studentAttendanceService.update(attendanceForm);
		model.addAttribute("message", message);
		// 一覧の再取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);

		return "attendance/detail";
	}

}