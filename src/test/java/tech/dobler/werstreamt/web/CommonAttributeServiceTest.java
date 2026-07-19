package tech.dobler.werstreamt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;
import tech.dobler.werstreamt.application.ListSelectionService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonAttributeServiceTest {

    @Mock
    private ListSelectionService listSelectionService;
    @Mock
    private Model model;

    @Test
    void addsTheSelectedListFromTheApplicationLayer() {
        when(listSelectionService.currentList()).thenReturn("some list name");

        new CommonAttributeService(listSelectionService).add(model);

        verify(model).addAttribute("selectedList", "some list name");
    }
}
