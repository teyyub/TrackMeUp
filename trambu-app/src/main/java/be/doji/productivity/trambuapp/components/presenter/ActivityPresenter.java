package be.doji.productivity.trambuapp.components.presenter;

import be.doji.productivity.trambuapp.components.view.ActivityView;
import be.doji.productivity.trambuapp.utils.DisplayConstants;
import be.doji.productivity.trambuapp.utils.DisplayUtils;
import be.doji.productivity.trambuapp.utils.TooltipConstants;
import be.doji.productivity.trambucore.managers.NoteManager;
import be.doji.productivity.trambucore.model.tasks.Activity;
import be.doji.productivity.trambucore.model.tasks.Note;
import be.doji.productivity.trambucore.model.tracker.ActivityLog;
import be.doji.productivity.trambucore.model.tracker.TimeLog;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class ActivityPresenter extends Presenter {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityPresenter.class);

    private ActivityManagerContainer managerContainer;
    private ActivityLog activityLog;
    private Activity model;
    private ActivityView view;
    private ActivityPagePresenter parent;
    private boolean modelParentChanged;
    private boolean editable;

    public ActivityPresenter(ActivityView view, Activity model, ActivityPagePresenter parent) {
        this(view, model);
        this.parent = parent;
    }

    public ActivityPresenter(ActivityView view, Activity model) {
        this.view = view;
        this.model = model;
        this.managerContainer = find(ActivityManagerContainer.class);
        this.activityLog = getActivityLog();
    }

    public ActivityLog getActivityLog() {
        return managerContainer.getTimeTrackingManager().getLogForActivityId(this.model.getId());
    }

    public void headerButtonClicked() {
        this.doneClicked();
    }

    public void refresh() {
        this.activityLog = getActivityLog();
        refreshHeader();
        refreshViewStyle();
        refreshControls();
        refreshFields();
        view.refreshContent();
    }

    public void populate() {
        this.refresh();
    }

    private void refreshHeader() {
        view.setText(getActivityName());
        view.getTitleLabel().setGraphic(getHeaderIcon());
        view.getTitleLabel().setTooltip(getDoneTooltipText());
    }

    private void refreshViewStyle() {
        view.getStyleClass()
                .removeAll(DisplayConstants.STYLE_CLASS_ACTIVITY_DONE, DisplayConstants.STYLE_CLASS_ACTIVITY_TODO,
                        DisplayConstants.STYLE_CLASS_ACTIVITY_ALERT);
        view.getStyleClass().add(getActivityStyle());
    }

    private void refreshControls() {
        refreshDoneButton();
    }

    private void refreshFields() {
        refreshNameField();
        refreshPriorityField();
        refreshLocationField();
        refreshWarningPerdiodField();
        refreshTagsFields();
        refreshProjectsField();
        refreshSubActivitiesField();
        refreshDeadlineField();
    }

    private void refreshNameField() {
        view.getNameField().setData(this.model.getName());
    }

    private void refreshPriorityField() {
        view.getPriorityField().setData(this.model.getPriority());
    }

    private void refreshLocationField() {
        if (this.model.isSetLocation()) {
            view.getLocationField().setData(this.model.getLocation());
        }
        view.getLocationField().getEditableField().getDataContainer().setSuggestions(this.getLocationSuggestions());
    }

    private void refreshWarningPerdiodField() {
        view.getWarningPeriodField().setData(this.model.getWarningTimeFrame().toString());
    }

    private void refreshTagsFields() {
        view.getTagsField().setData(this.model.getTags());
        view.getTagsField().getEditableField().getDataContainer().setSuggestions(getTagSuggestions());
    }

    private void refreshProjectsField() {
        view.getProjectsField().setData(this.model.getProjects());
        view.getTagsField().getEditableField().getDataContainer().setSuggestions(getProjectSuggestions());
    }

    private void refreshSubActivitiesField() {
        if (view.getSubActivitiesAccordion() != null) {
            ObservableList<TitledPane> panes = view.getSubActivitiesAccordion().getPanes();
            panes.clear();
            for (Activity subActivity : this.model.getSubActivities()) {
                panes.add(new ActivityView(subActivity));
            }
        }
    }

    private void refreshDeadlineField() {
        if (this.model.isSetDeadline()) {
            view.getDeadlineField().setData(this.model.getDeadline());
        }
    }

    private void updateModel() {
        this.model.setName(view.getNameField().update().getData());
        this.model.setPriority(view.getPriorityField().update().getData());
        this.model.setLocation(view.getLocationField().update().getData());
        parseWarningPeriod(view.getWarningPeriodField().update().getData())
                .ifPresent(d -> this.model.setWarningTimeFrame(d));
        this.model.setTags(view.getTagsField().update().getData());
        this.model.setProjects(view.getProjectsField().update().getData());
        this.model.setDeadline(view.getDeadlineField().update().getData());
    }

    private Optional<Duration> parseWarningPeriod(String warningPeriod) {
        if (validateWarningPeriod(warningPeriod)) {
            Duration timeFrame = Duration.ofHours(Long.parseLong(warningPeriod));
            return Optional.of(timeFrame);
        } else {
            return Optional.empty();
        }
    }

    private boolean validateWarningPeriod(String warningPeriod) {
        return StringUtils.isNotBlank(warningPeriod) && warningPeriod.matches(DisplayConstants.REGEX_WARNING_PERIOD);
    }

    private FontAwesomeIconView getHeaderIcon() {
        FontAwesomeIconView checkedCalendar = DisplayUtils.createStyledIcon(FontAwesomeIcon.CHECK_CIRCLE);
        FontAwesomeIconView uncheckedCalendar = DisplayUtils.createStyledIcon(FontAwesomeIcon.CIRCLE_ALT);
        FontAwesomeIconView editing = DisplayUtils.createStyledIcon(FontAwesomeIcon.EDIT);
        if (this.isEditable()) {
            return editing;
        } else {
            return model.isCompleted()?checkedCalendar:uncheckedCalendar;
        }
    }

    private Tooltip getDoneTooltipText() {
        return DisplayUtils.createTooltip(model.isCompleted()?
                TooltipConstants.TOOLTIP_TEXT_ACTIVITY_NOT_DONE:
                TooltipConstants.TOOLTIP_TEXT_ACTIVITY_DONE);
    }

    public String getActivityName() {
        return model.getName();
    }

    private String getActivityStyle() {
        if (this.model.isCompleted() && this.model.isAllSubActivitiesCompleted()) {
            return DisplayConstants.STYLE_CLASS_ACTIVITY_DONE;
        } else {
            return this.model.isAlertActive()?
                    DisplayConstants.STYLE_CLASS_ACTIVITY_ALERT:
                    DisplayConstants.STYLE_CLASS_ACTIVITY_TODO;
        }
    }

    public boolean hasSubActivities() {
        return !this.model.getSubActivities().isEmpty();
    }

    private void save() throws IOException, ParseException {
        updateModel();
        this.managerContainer.getActivityManager().save(getActivityToSave());
        if (!this.modelParentChanged) {
            refresh();
        } else if (this.parent != null) {
            this.parent.refresh();
        }
    }

    private Activity getActivityToSave() {
        if (!this.modelParentChanged) {
            return this.model;
        } else {
            return getRootActivity();
        }

    }

    @NotNull private Activity getRootActivity() {
        Activity activityToSave = this.model;
        while (StringUtils.isNotBlank(activityToSave.getParentActivity())) {
            Optional<Activity> savedParent = this.managerContainer.getActivityManager()
                    .getSavedActivityById(activityToSave.getParentActivity());
            if (savedParent.isPresent()) {
                activityToSave = savedParent.get();
            }
        }
        return activityToSave;
    }

    public void openNotes() {
        try {
            NoteManager noteManager = this.managerContainer.getNoteManager();
            Optional<Note> noteForActivity = noteManager.findNoteForActivity(this.model.getId());
            Note note;
            if (noteForActivity.isPresent()) {
                note = noteForActivity.get();
            } else {
                note = noteManager.createNoteForActivity(this.model.getId());
            }

            TextArea textField = new TextArea();
            textField.setPrefWidth(view.getOverlay().getWidth());
            textField.setPrefHeight(view.getOverlay().getHeight());
            textField.setText(note.getContent().stream().collect(Collectors.joining(System.lineSeparator())));
            textField.setWrapText(true);
            textField.setEditable(true);
            view.getOverlay().setContent(textField);
            view.getOverlay().setControlButtons(createNoteControlButtons(note, textField));
            view.getOverlay().refreshContent();
            view.getOverlay().setVisible(true);
        } catch (IOException e) {
            view.getOverlay().setContent(new Label("Error reading notes: " + e.getMessage()));
        }
    }

    private List<Button> createNoteControlButtons(Note noteToSave, TextArea textField) {
        List<Button> controls = new ArrayList<>();
        Button saveButton = new Button("Save changes");
        saveButton.setGraphic(DisplayUtils.createStyledIcon(FontAwesomeIcon.SAVE));
        saveButton.setTooltip(DisplayUtils.createTooltip(TooltipConstants.TOOLTIP_TEXT_ACTIVITY_SAVE_NOTE));
        saveButton.setOnAction(event -> {
            try {
                noteToSave.setContent(Arrays.asList(textField.getText().split(System.lineSeparator())));
                noteToSave.save();
            } catch (IOException e) {
                LOG.error("Error saving note to file: " + e.getMessage());
            }
        });
        controls.add(saveButton);
        return controls;
    }

    public List<String> getPossibleParents() {
        List<String> possibleParents = this.managerContainer.getActivityManager().getAllActivityNames();
        possibleParents.remove(this.model.getName());
        return possibleParents;
    }

    public void changeParent(String newParent) {
        Optional<Activity> savedParent = this.managerContainer.getActivityManager().getSavedActivityByName(newParent);
        savedParent.ifPresent(
                activity -> this.managerContainer.getActivityManager().addActivityAsSub(this.model, activity));
        this.modelParentChanged = true;
    }

    private void refreshDoneButton() {
        view.getDoneButton().setText(DisplayUtils.getDoneButtonText(this.model));
        view.getDoneButton().setTooltip(getDoneTooltipText());
    }

    public void doneClicked() {
        try {
            if (!this.model.isCompleted() && !this.model.isAllSubActivitiesCompleted()) {
                LOG.warn("Completing activity with incomplete subactivities");
            }

            this.model.setCompleted(!model.isCompleted());
            view.getDoneButton().setText(DisplayUtils.getDoneButtonText(this.model));
            view.getDoneButton().setTooltip(getDoneTooltipText());
            save();
            refreshHeader();
        } catch (IOException | ParseException e) {
            LOG.error(DisplayConstants.ERROR_MESSAGE_ACTIVITY_SAVING + ": " + e.getMessage());
        }
    }

    public void editButtonClicked() {
        try {
            if (this.isEditable()) {
                save();
                this.makeAllFieldsStaticAndRefresh();
            } else {
                this.makeAllFieldsEditableAndRefresh();
            }
            view.refreshContent();
            view.getEditButton().setText(getEditButonText());
        } catch (IOException | ParseException e) {
            LOG.error(DisplayConstants.ERROR_MESSAGE_ACTIVITY_SAVING + ": " + e.getMessage());
        }
    }

    @NotNull private SortedSet<String> getProjectSuggestions() {
        SortedSet<String> treeSetProjects = new TreeSet<>();
        treeSetProjects.addAll(this.managerContainer.getActivityManager().getExistingProjects());
        return treeSetProjects;
    }

    @NotNull private SortedSet<String> getTagSuggestions() {
        SortedSet<String> treeSetProjects = new TreeSet<>();
        treeSetProjects.addAll(this.managerContainer.getActivityManager().getExistingTags());
        return treeSetProjects;
    }

    @NotNull private SortedSet<String> getLocationSuggestions() {
        SortedSet<String> treeSetProjects = new TreeSet<>();
        treeSetProjects.addAll(this.managerContainer.getActivityManager().getExistingLocations());
        return treeSetProjects;
    }

    public String getEditButonText() {
        return this.isEditable()?DisplayConstants.BUTTON_TEXT_SAVE:DisplayConstants.BUTTON_TEXT_EDIT;
    }

    public void deleteButtonClicked() {
        try {
            this.managerContainer.getActivityManager().delete(this.model);
            view.setVisible(false);
            if (this.parent != null) {
                parent.refresh();
            }
        } catch (IOException | ParseException e) {
            LOG.error(DisplayConstants.ERROR_MESSAGE_ACTIVITY_SAVING + ": " + e.getMessage());
        }
    }

    public void timingButtonPressed() {
        if (getActiveLog().isPresent()) {
            activityLog.stopActiveLog();
        } else {
            activityLog.startLog();
        }
        view.getTimingButton().setText(getTimingButtonText());
        view.getTimingButton().setGraphic(getTimingButtonIcon());
        this.managerContainer.getTimeTrackingManager().save(activityLog);
    }

    private Optional<TimeLog> getActiveLog() {
        return getActivityLog().getActiveLog();
    }

    public String getTimingButtonText() {
        Optional<TimeLog> activeLog = getActiveLog();
        if (activeLog.isPresent()) {
            return DisplayConstants.BUTTON_TEXT_TIMER_STOP;
        } else {
            return DisplayConstants.BUTTON_TEXT_TIMER_START;
        }
    }

    public FontAwesomeIconView getTimingButtonIcon() {
        return DisplayUtils.createStyledIcon(
                activityLog.getActiveLog().isPresent()?FontAwesomeIcon.HOURGLASS_END:FontAwesomeIcon.HOURGLASS_START);
    }

    public Tooltip getTimingButtonTooltipText() {
        return DisplayUtils.createTooltip(getActiveLog().isPresent()?
                TooltipConstants.TOOLTIP_TEXT_ACTIVITY_TIMING_CONTROL_STOP:
                TooltipConstants.TOOLTIP_TEXT_ACTIVITY_TIMING_CONTROL_START);
    }

    public void setTagFilter(String tag) {
        if (parent != null) {
            parent.setTagFilter(tag);
            parent.refresh();
        }
    }

    public void setProjectFilter(String project) {
        if (parent != null) {
            parent.setProjectFilter(project);
            parent.refresh();
        }
    }

    public boolean isEditable() {
        return this.editable;
    }

    public void makeAllFieldsEditableAndRefresh() {
        this.editable = true;
        view.getNameField().makeEditable();
        view.getPriorityField().makeEditable();
        view.getLocationField().makeEditable();
        view.getTagsField().makeEditable();
        view.getProjectsField().makeEditable();
        view.getDeadlineField().makeEditable();
        view.refresh();
    }

    public void makeAllFieldsStaticAndRefresh() {
        this.editable = true;
        view.getNameField().makeStatic();
        view.getPriorityField().makeStatic();
        view.getLocationField().makeStatic();
        view.getTagsField().makeStatic();
        view.getProjectsField().makeStatic();
        view.getDeadlineField().makeStatic();
        view.refresh();
    }
}
